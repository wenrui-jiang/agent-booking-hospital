#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/runtime-logs"
PID_DIR="$LOG_DIR/pids"
MYSQL_CONTAINER="${YYGH_MYSQL_CONTAINER:-yygh-mysql}"

mkdir -p "$LOG_DIR" "$PID_DIR"

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

stop_one() {
  local name="$1"
  local file="$PID_DIR/$name.pid"
  if [[ ! -f "$file" ]]; then
    echo "$name has no pid file"
    return
  fi

  local pid
  pid="$(cat "$file")"
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

BACKEND_ROOT="$(find "$ROOT" -type d -name yygh_parent -print -quit)"
FRONTEND_ROOT="$(find "$ROOT" -type d -name yygh-site -print -quit)"

if [[ -z "$BACKEND_ROOT" || -z "$FRONTEND_ROOT" ]]; then
  echo "[ERROR] Cannot find backend or frontend root." >&2
  exit 1
fi

stop_one service-agent
stop_one yygh-site

AGENT_JAR="$(jar_for "$BACKEND_ROOT/service/service_agent")"
if [[ -z "$AGENT_JAR" ]]; then
  echo "[ERROR] Cannot find service-agent jar. Build service_agent first." >&2
  exit 1
fi

(
  cd "$BACKEND_ROOT/service/service_agent"
  nohup env YYGH_MYSQL_PASSWORD="$MYSQL_PASSWORD" java -jar "$AGENT_JAR" \
    --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
    >"$LOG_DIR/service-agent.out.log" 2>"$LOG_DIR/service-agent.err.log" &
  echo $! >"$PID_DIR/service-agent.pid"
)
echo "Started service-agent (PID $(cat "$PID_DIR/service-agent.pid"))"

(
  cd "$FRONTEND_ROOT"
  nohup env NODE_OPTIONS=--openssl-legacy-provider HOST=127.0.0.1 PORT=3000 NUXT_HOST=127.0.0.1 NUXT_PORT=3000 \
    node node_modules/nuxt/bin/nuxt.js start \
    >"$LOG_DIR/yygh-site.out.log" 2>"$LOG_DIR/yygh-site.err.log" &
  echo $! >"$PID_DIR/yygh-site.pid"
)
echo "Started yygh-site (PID $(cat "$PID_DIR/yygh-site.pid"))"

sleep 8
for name in service-agent yygh-site; do
  pid="$(cat "$PID_DIR/$name.pid")"
  if kill -0 "$pid" >/dev/null 2>&1; then
    echo "$name running (PID $pid)"
  else
    echo "[ERROR] $name failed to stay running" >&2
    exit 1
  fi
done

curl -s -o /dev/null -w "service-agent:%{http_code}\n" http://127.0.0.1:8210/api/agent/sessions || true
curl -s -o /dev/null -w "yygh-site:%{http_code}\n" http://127.0.0.1:3000/ || true
