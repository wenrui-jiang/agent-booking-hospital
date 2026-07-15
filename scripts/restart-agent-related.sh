#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/runtime-logs"
PID_DIR="$LOG_DIR/pids"
MYSQL_CONTAINER="${YYGH_MYSQL_CONTAINER:-yygh-mysql}"

mkdir -p "$LOG_DIR" "$PID_DIR"

load_env_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$file"
    set +a
  fi
}

load_env_file "$ROOT/.env"
load_env_file "$ROOT/deploy/cloud/.env"
load_env_file "$ROOT/.env.agent.local"
load_env_file "$ROOT/.env.mysql.local"

detect_mysql_password_from_docker() {
  docker inspect "$MYSQL_CONTAINER" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | grep '^MYSQL_ROOT_PASSWORD=' \
    | sed 's/^MYSQL_ROOT_PASSWORD=//' \
    | tail -n 1
}

MYSQL_PASSWORD="${YYGH_MYSQL_PASSWORD:-}"
if [[ -z "$MYSQL_PASSWORD" ]]; then
  MYSQL_PASSWORD="$(detect_mysql_password_from_docker || true)"
fi

if [[ -z "$MYSQL_PASSWORD" ]]; then
  echo "[ERROR] Unable to determine MySQL password for service-agent." >&2
  exit 1
fi

BACKEND_ROOT="$ROOT/backend/yygh_parent"
FRONTEND_ROOT="$ROOT/frontend/yygh-site"

if [[ ! -d "$BACKEND_ROOT" ]]; then
  BACKEND_ROOT="$(find "$ROOT" -type d -name yygh_parent -print -quit)"
fi

if [[ ! -d "$FRONTEND_ROOT" ]]; then
  FRONTEND_ROOT="$(find "$ROOT" -type d -name yygh-site -print -quit)"
fi

if [[ -z "$BACKEND_ROOT" || -z "$FRONTEND_ROOT" ]]; then
  echo "[ERROR] Cannot find backend or frontend root." >&2
  exit 1
fi

stop_one() {
  local name="$1"
  local file="$PID_DIR/$name.pid"
  local pid=""
  if [[ ! -f "$file" ]]; then
    case "$name" in
      service-agent)
        pid="$(pgrep -f "$BACKEND_ROOT/service/service_agent/target/.*\\.jar" | head -n 1 || true)"
        ;;
      agent-langgraph)
        pid="$(pgrep -f "uvicorn app.main:app .*--port ${YYGH_LANGGRAPH_PORT:-8212}" | head -n 1 || true)"
        ;;
      yygh-site)
        pid="$(pgrep -f "node node_modules/nuxt/bin/nuxt\\.js start" | head -n 1 || true)"
        ;;
    esac
    if [[ -z "$pid" ]]; then
      echo "$name has no pid file"
      return
    fi
  else
    pid="$(cat "$file")"
  fi

  if kill -0 "$pid" >/dev/null 2>&1; then
    pkill -TERM -P "$pid" >/dev/null 2>&1 || true
    kill "$pid" >/dev/null 2>&1 || true
    echo "Stopped $name (PID $pid)"
    sleep 2
  else
    echo "$name pid file was stale (PID $pid)"
  fi
  rm -f "$file"
}

jar_for() {
  local workdir="$1"
  find "$workdir" -type f -name '*.jar' ! -path '*/original/*' -print | head -n 1
}

wait_http() {
  local name="$1"
  local url="$2"
  local timeout_seconds="${3:-60}"
  local deadline=$((SECONDS + timeout_seconds))
  local status=""
  while [[ "$SECONDS" -lt "$deadline" ]]; do
    status="$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)"
    if [[ "$status" != "000" ]]; then
      echo "$name ready ($status)"
      return 0
    fi
    sleep 2
  done
  echo "[ERROR] $name did not become reachable at $url" >&2
  return 1
}

stop_one service-agent
stop_one agent-langgraph
stop_one yygh-site

AGENT_JAR="$(jar_for "$BACKEND_ROOT/service/service_agent")"
if [[ -z "$AGENT_JAR" ]]; then
  echo "[ERROR] Cannot find service-agent jar. Build service_agent first." >&2
  exit 1
fi

(
  cd "$ROOT/agent-langgraph"
  LANGGRAPH_PORT="${YYGH_LANGGRAPH_PORT:-8212}"
  setsid env \
    DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}" \
    DEEPSEEK_BASE_URL="${DEEPSEEK_BASE_URL:-https://api.deepseek.com}" \
    DEEPSEEK_MODEL="${DEEPSEEK_MODEL:-deepseek-v4-pro}" \
    JAVA_AGENT_BASE_URL="${JAVA_AGENT_BASE_URL:-http://127.0.0.1:8210}" \
    PGVECTOR_DSN="${PGVECTOR_DSN:-}" \
    YYGH_AGENT_INTERNAL_SECRET="${YYGH_AGENT_INTERNAL_SECRET:-local-dev-agent-secret}" \
    python3 -m uvicorn app.main:app --host 127.0.0.1 --port "$LANGGRAPH_PORT" \
    >"$LOG_DIR/agent-langgraph.out.log" 2>"$LOG_DIR/agent-langgraph.err.log" < /dev/null &
  echo $! >"$PID_DIR/agent-langgraph.pid"
)
echo "Started agent-langgraph (PID $(cat "$PID_DIR/agent-langgraph.pid"))"

(
  cd "$BACKEND_ROOT/service/service_agent"
  setsid env \
    YYGH_MYSQL_PASSWORD="$MYSQL_PASSWORD" \
    YYGH_LANGGRAPH_ENABLED="${YYGH_LANGGRAPH_ENABLED:-true}" \
    YYGH_LANGGRAPH_BASE_URL="${YYGH_LANGGRAPH_BASE_URL:-http://127.0.0.1:8212}" \
    DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}" \
    DEEPSEEK_BASE_URL="${DEEPSEEK_BASE_URL:-https://api.deepseek.com}" \
    DEEPSEEK_MODEL="${DEEPSEEK_MODEL:-deepseek-v4-pro}" \
    java -jar "$AGENT_JAR" \
    --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
    >"$LOG_DIR/service-agent.out.log" 2>"$LOG_DIR/service-agent.err.log" < /dev/null &
  echo $! >"$PID_DIR/service-agent.pid"
)
echo "Started service-agent (PID $(cat "$PID_DIR/service-agent.pid"))"

(
  cd "$FRONTEND_ROOT"
  setsid env NODE_OPTIONS=--openssl-legacy-provider HOST=127.0.0.1 PORT=3000 NUXT_HOST=127.0.0.1 NUXT_PORT=3000 \
    node node_modules/nuxt/bin/nuxt.js start \
    >"$LOG_DIR/yygh-site.out.log" 2>"$LOG_DIR/yygh-site.err.log" < /dev/null &
  echo $! >"$PID_DIR/yygh-site.pid"
)
echo "Started yygh-site (PID $(cat "$PID_DIR/yygh-site.pid"))"

wait_http agent-langgraph "http://127.0.0.1:${YYGH_LANGGRAPH_PORT:-8212}/health" 30
wait_http service-agent "http://127.0.0.1:8210/api/agent/sessions" 90
wait_http yygh-site "http://127.0.0.1:3000/" 60

for name in agent-langgraph service-agent yygh-site; do
  pid="$(cat "$PID_DIR/$name.pid")"
  if kill -0 "$pid" >/dev/null 2>&1; then
    echo "$name running (PID $pid)"
  else
    echo "[ERROR] $name failed to stay running" >&2
    exit 1
  fi
done
