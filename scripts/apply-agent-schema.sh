#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEMA_FILE="${1:-$ROOT/docs/yygh_agent_schema.sql}"
MYSQL_CONTAINER="${YYGH_MYSQL_CONTAINER:-yygh-mysql}"

if [[ ! -f "$SCHEMA_FILE" ]]; then
  echo "[ERROR] Schema file not found: $SCHEMA_FILE" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker command not found." >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -x "$MYSQL_CONTAINER" >/dev/null 2>&1; then
  echo "[ERROR] MySQL container is not running: $MYSQL_CONTAINER" >&2
  exit 1
fi

MYSQL_PASSWORD="${YYGH_MYSQL_PASSWORD:-}"
if [[ -z "$MYSQL_PASSWORD" ]]; then
  MYSQL_PASSWORD="$(docker inspect "$MYSQL_CONTAINER" --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | grep '^MYSQL_ROOT_PASSWORD=' \
    | sed 's/^MYSQL_ROOT_PASSWORD=//' \
    | tail -n 1)"
fi

if [[ -z "$MYSQL_PASSWORD" ]]; then
  echo "[ERROR] Unable to determine MySQL root password." >&2
  exit 1
fi

docker cp "$SCHEMA_FILE" "$MYSQL_CONTAINER:/tmp/yygh_agent_schema.sql"
docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER" \
  mysql -uroot -e "source /tmp/yygh_agent_schema.sql"

echo "[INFO] Applied agent schema from $SCHEMA_FILE"
docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER" \
  mysql -uroot -N -e "use yygh_agent; show tables"
