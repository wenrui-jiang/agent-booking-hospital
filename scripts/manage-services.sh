#!/usr/bin/env bash

set -euo pipefail

ACTION="${1:-start}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/runtime-logs"
PID_DIR="$LOG_DIR/pids"
LOCAL_ENV_FILE="${YYGH_LOCAL_ENV_FILE:-$ROOT/.env.mail.local}"
CLOUD_ENV_FILE="$ROOT/deploy/cloud/.env"
MYSQL_ENV_FILE="$ROOT/.env.mysql.local"

if [[ -f "$CLOUD_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$CLOUD_ENV_FILE"
fi

if [[ -f "$LOCAL_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$LOCAL_ENV_FILE"
fi

if [[ -f "$MYSQL_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$MYSQL_ENV_FILE"
fi

MYSQL_USERNAME="${YYGH_MYSQL_USERNAME:-root}"
MYSQL_PASSWORD="${YYGH_MYSQL_PASSWORD:-}"

detect_mysql_password_from_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    return 1
  fi
  if ! docker ps -a --format '{{.Names}}' 2>/dev/null | grep -x 'yygh-mysql' >/dev/null 2>&1; then
    return 1
  fi
  docker inspect yygh-mysql --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | grep '^MYSQL_ROOT_PASSWORD=' \
    | awk -F '=' '{print $2}' \
    | tr -d '\r' \
    | tail -n 1
}

if [[ -z "$MYSQL_PASSWORD" ]]; then
  AUTO_DETECTED_MYSQL_PASSWORD="$(detect_mysql_password_from_docker || true)"
  if [[ -n "$AUTO_DETECTED_MYSQL_PASSWORD" ]]; then
    MYSQL_PASSWORD="$AUTO_DETECTED_MYSQL_PASSWORD"
    export YYGH_MYSQL_PASSWORD="$MYSQL_PASSWORD"
    echo "[INFO] Auto-detected YYGH_MYSQL_PASSWORD from docker container yygh-mysql."
  fi
fi

if [[ -z "$MYSQL_PASSWORD" && "$ACTION" != "stop" && "$ACTION" != "status" ]]; then
  cat <<'EOF'
[ERROR] YYGH_MYSQL_PASSWORD is empty.
Please choose one of the following fixes before starting services:
  1) Export password for current shell:
     export YYGH_MYSQL_PASSWORD='your_mysql_root_password'
  2) Persist in project file:
     echo "export YYGH_MYSQL_PASSWORD='your_mysql_root_password'" > .env.mysql.local
  3) Ensure docker container name is yygh-mysql so password can be auto-detected.
EOF
  exit 1
fi

MAIL_SMTP_HOST="${YYGH_MAIL_SMTP_HOST:-smtp.fastmail.com}"
MAIL_SMTP_PORT="${YYGH_MAIL_SMTP_PORT:-465}"
MAIL_SMTP_USERNAME="${YYGH_MAIL_SMTP_USERNAME:-hello@jiangwenrui.com}"
MAIL_SMTP_AUTH_USERNAME="${YYGH_MAIL_SMTP_AUTH_USERNAME:-}"
MAIL_FROM_ADDRESS="${YYGH_MAIL_FROM_ADDRESS:-hello@jiangwenrui.com}"
MAIL_FROM_NAME="${YYGH_MAIL_FROM_NAME:-Jiangwenrui}"
MAIL_SSL="${YYGH_MAIL_SSL:-true}"
MAIL_STARTTLS="${YYGH_MAIL_STARTTLS:-false}"

MYSQL_PASSWORD_ARGS=()
if [[ -n "$MYSQL_PASSWORD" ]]; then
  MYSQL_PASSWORD_ARGS=("--spring.datasource.password=$MYSQL_PASSWORD")
fi

MAIL_AUTH_USERNAME_ARGS=()
if [[ -n "$MAIL_SMTP_AUTH_USERNAME" ]]; then
  MAIL_AUTH_USERNAME_ARGS=("--yygh.mail.auth-username=$MAIL_SMTP_AUTH_USERNAME")
fi

mkdir -p "$LOG_DIR" "$PID_DIR"
LOG_MAX_BYTES="${YYGH_SERVICE_LOG_MAX_BYTES:-10485760}"
LOG_ROTATE_KEEP="${YYGH_SERVICE_LOG_ROTATE_KEEP:-3}"

BACKEND_ROOT="$(find "$ROOT" -type d -name yygh_parent -print -quit)"
FRONTEND_ROOT="$(find "$ROOT" -type d -name yygh-site -print -quit)"

if [[ -z "$BACKEND_ROOT" ]]; then
  echo "[ERROR] Cannot find backend root directory: yygh_parent"
  exit 1
fi

if [[ -z "$FRONTEND_ROOT" ]]; then
  echo "[ERROR] Cannot find frontend root directory: yygh-site"
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "[ERROR] Cannot find java. Please install JDK8 and add java to PATH."
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "[ERROR] Cannot find npm. Please install Node.js and npm."
  exit 1
fi

service_names=(
  service-hosp
  service-cmn
  service-user
  service-order
  service-msm
  service-oss
  service-task
  service-statistics
  service-agent
  service-gateway
  yygh-site
)

pid_file() {
  echo "$PID_DIR/$1.pid"
}

service_pid() {
  local name="$1"
  local file
  file="$(pid_file "$name")"
  if [[ -f "$file" ]]; then
    local saved_pid
    saved_pid="$(cat "$file")"
    if kill -0 "$saved_pid" >/dev/null 2>&1; then
      echo "$saved_pid"
      return 0
    fi
    rm -f "$file"
  fi
  if [[ "$name" == "yygh-site" && -n "${FRONTEND_ROOT:-}" ]]; then
    local detected_pid
    detected_pid="$(pgrep -f "$FRONTEND_ROOT/node_modules/(\\.bin/nuxt|nuxt/bin/nuxt.js) start" | head -n 1 || true)"
    if [[ -n "$detected_pid" ]]; then
      echo "$detected_pid" >"$file"
      echo "$detected_pid"
      return 0
    fi
  fi
  return 1
}

jar_for() {
  local workdir="$1"
  local pattern="$2"
  find "$workdir" -type f -name "$pattern" ! -path '*/original/*' -print | head -n 1
}

rotate_log_if_needed() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    return
  fi

  local size
  size="$(wc -c <"$file" 2>/dev/null || echo 0)"
  if [[ "$size" -le "$LOG_MAX_BYTES" ]]; then
    return
  fi

  local stamp
  stamp="$(date +%Y%m%d%H%M%S)"
  mv "$file" "$file.$stamp"
  find "$(dirname "$file")" -maxdepth 1 -name "$(basename "$file").*" -type f -printf '%T@ %p\n' |
    sort -nr |
    awk -v keep="$LOG_ROTATE_KEEP" 'NR > keep { print $2 }' |
    xargs -r rm -f
}

prepare_service_logs() {
  local name="$1"
  rotate_log_if_needed "$LOG_DIR/$name.out.log"
  rotate_log_if_needed "$LOG_DIR/$name.err.log"
}

start_java_service() {
  local name="$1"
  local workdir="$2"
  local pattern="$3"
  shift 3

  if service_pid "$name" >/dev/null; then
    echo "$name already running (PID $(service_pid "$name"))"
    return
  fi

  local jar
  jar="$(jar_for "$workdir" "$pattern")"
  if [[ -z "$jar" ]]; then
    echo "[ERROR] Cannot find jar for $name under $workdir. Build it first."
    exit 1
  fi

  prepare_service_logs "$name"
  (
    cd "$workdir"
    nohup java -jar "$jar" "$@" >"$LOG_DIR/$name.out.log" 2>"$LOG_DIR/$name.err.log" &
    echo $! >"$(pid_file "$name")"
  )
  echo "Started $name (PID $(cat "$(pid_file "$name")"))"
}

start_npm_service() {
  local name="yygh-site"
  if service_pid "$name" >/dev/null; then
    echo "$name already running (PID $(service_pid "$name"))"
    return
  fi

  prepare_service_logs "$name"
  (
    cd "$FRONTEND_ROOT"
    NODE_OPTIONS=--openssl-legacy-provider npm run build
    nohup env NODE_OPTIONS=--openssl-legacy-provider HOST=127.0.0.1 PORT=3000 NUXT_HOST=127.0.0.1 NUXT_PORT=3000 node node_modules/nuxt/bin/nuxt.js start >"$LOG_DIR/$name.out.log" 2>"$LOG_DIR/$name.err.log" &
    echo $! >"$(pid_file "$name")"
  )
  echo "Started $name (PID $(cat "$(pid_file "$name")"))"
}

start_all() {
  start_java_service service-hosp "$BACKEND_ROOT/service/service_hosp" 'service-hosp*.jar' \
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_hosp?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai' \
    "--spring.datasource.username=$MYSQL_USERNAME" \
    "${MYSQL_PASSWORD_ARGS[@]}" \
    '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp' \
    '--spring.data.mongodb.auto-index-creation=false' \
    '--spring.rabbitmq.host=127.0.0.1' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-cmn "$BACKEND_ROOT/service/service_cmn" '*.jar' \
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_cmn?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai' \
    "--spring.datasource.username=$MYSQL_USERNAME" \
    "${MYSQL_PASSWORD_ARGS[@]}" \
    '--spring.rabbitmq.host=127.0.0.1' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-user "$BACKEND_ROOT/service/service_user" '*.jar' \
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_user?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai' \
    "--spring.datasource.username=$MYSQL_USERNAME" \
    "${MYSQL_PASSWORD_ARGS[@]}" \
    '--spring.rabbitmq.host=127.0.0.1' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-order "$BACKEND_ROOT/service/service_order" '*.jar' \
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_order?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai' \
    "--spring.datasource.username=$MYSQL_USERNAME" \
    "${MYSQL_PASSWORD_ARGS[@]}" \
    '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp' \
    '--spring.data.mongodb.auto-index-creation=false' \
    '--spring.rabbitmq.host=127.0.0.1' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848' \
    '--yygh.order.mock-hospital-submit=true'

  start_java_service service-msm "$BACKEND_ROOT/service/service_msm" '*.jar' \
    '--spring.rabbitmq.host=127.0.0.1' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848' \
    '--spring.redis.host=127.0.0.1' \
    '--spring.redis.port=6379' \
    '--yygh.msm.dev-mode=false' \
    '--yygh.mail.enabled=true' \
    "--yygh.mail.host=$MAIL_SMTP_HOST" \
    "--yygh.mail.port=$MAIL_SMTP_PORT" \
    "--yygh.mail.username=$MAIL_SMTP_USERNAME" \
    "${MAIL_AUTH_USERNAME_ARGS[@]}" \
    "--yygh.mail.from-address=$MAIL_FROM_ADDRESS" \
    "--yygh.mail.from-name=$MAIL_FROM_NAME" \
    "--yygh.mail.ssl=$MAIL_SSL" \
    "--yygh.mail.starttls=$MAIL_STARTTLS"

  start_java_service service-oss "$BACKEND_ROOT/service/service_oss" '*.jar' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-task "$BACKEND_ROOT/service/service_task" '*.jar' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-statistics "$BACKEND_ROOT/service/service_statistics" '*.jar' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-agent "$BACKEND_ROOT/service/service_agent" '*.jar' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-gateway "$BACKEND_ROOT/service_gateway" '*.jar' \
    '--server.port=8080' \
    '--spring.profiles.active=dev' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_npm_service
}

stop_all() {
  for name in "${service_names[@]}"; do
    if pid="$(service_pid "$name")"; then
      pkill -TERM -P "$pid" >/dev/null 2>&1 || true
      kill "$pid" >/dev/null 2>&1 || true
      rm -f "$(pid_file "$name")"
      echo "Stopped $name (PID $pid)"
    else
      echo "$name is not running"
    fi
  done
}

status_all() {
  for name in "${service_names[@]}"; do
    if pid="$(service_pid "$name")"; then
      echo "$name: running (PID $pid)"
    else
      echo "$name: stopped"
    fi
  done
}

case "$ACTION" in
  start)
    start_all
    ;;
  stop)
    stop_all
    ;;
  restart)
    stop_all
    sleep 2
    start_all
    ;;
  status)
    status_all
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac
