#!/usr/bin/env bash

set -euo pipefail

ACTION="${1:-start}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/runtime-logs"
PID_DIR="$LOG_DIR/pids"
MYSQL_USERNAME="${YYGH_MYSQL_USERNAME:-root}"
MYSQL_PASSWORD="${YYGH_MYSQL_PASSWORD:-}"
LOCAL_ENV_FILE="${YYGH_LOCAL_ENV_FILE:-$ROOT/.env.mail.local}"

if [[ -f "$LOCAL_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$LOCAL_ENV_FILE"
fi

MAIL_SMTP_HOST="${YYGH_MAIL_SMTP_HOST:-smtp.fastmail.com}"
MAIL_SMTP_PORT="${YYGH_MAIL_SMTP_PORT:-465}"
MAIL_SMTP_USERNAME="${YYGH_MAIL_SMTP_USERNAME:-hello@jiangwenrui.com}"
MAIL_SMTP_AUTH_USERNAME="${YYGH_MAIL_SMTP_AUTH_USERNAME:-}"
MAIL_FROM_ADDRESS="${YYGH_MAIL_FROM_ADDRESS:-hello@jiangwenrui.com}"
MAIL_FROM_NAME="${YYGH_MAIL_FROM_NAME:-Jiangwenrui}"
MAIL_SSL="${YYGH_MAIL_SSL:-true}"
MAIL_STARTTLS="${YYGH_MAIL_STARTTLS:-false}"

mkdir -p "$LOG_DIR" "$PID_DIR"

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
  return 1
}

jar_for() {
  local workdir="$1"
  local pattern="$2"
  find "$workdir" -type f -name "$pattern" ! -path '*/original/*' -print | head -n 1
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

  (
    cd "$FRONTEND_ROOT"
    nohup env NODE_OPTIONS=--openssl-legacy-provider npm run dev >"$LOG_DIR/$name.out.log" 2>"$LOG_DIR/$name.err.log" &
    echo $! >"$(pid_file "$name")"
  )
  echo "Started $name (PID $(cat "$(pid_file "$name")"))"
}

start_all() {
  start_java_service service-hosp "$BACKEND_ROOT/service/service_hosp" 'service-hosp*.jar' \
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_hosp?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai' \
    "--spring.datasource.username=$MYSQL_USERNAME" \
    "--spring.datasource.password=$MYSQL_PASSWORD" \
    '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp' \
    '--spring.rabbitmq.host=127.0.0.1' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-cmn "$BACKEND_ROOT/service/service_cmn" '*.jar' \
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_cmn?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai' \
    "--spring.datasource.username=$MYSQL_USERNAME" \
    "--spring.datasource.password=$MYSQL_PASSWORD" \
    '--spring.rabbitmq.host=127.0.0.1' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-user "$BACKEND_ROOT/service/service_user" '*.jar' \
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_user?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai' \
    "--spring.datasource.username=$MYSQL_USERNAME" \
    "--spring.datasource.password=$MYSQL_PASSWORD" \
    '--spring.rabbitmq.host=127.0.0.1' \
    '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'

  start_java_service service-order "$BACKEND_ROOT/service/service_order" '*.jar' \
    '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_order?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai' \
    "--spring.datasource.username=$MYSQL_USERNAME" \
    "--spring.datasource.password=$MYSQL_PASSWORD" \
    '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp' \
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
    "--yygh.mail.auth-username=$MAIL_SMTP_AUTH_USERNAME" \
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
