#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Uso: $0 postgres|mysql <archivo>" >&2
  exit 1
}

[[ $# -eq 2 ]] || usage
ENGINE="$1"
FILE="$2"
[[ -f "$FILE" ]] || { echo "Archivo no encontrado: $FILE" >&2; exit 1; }

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
set -a
source .env
set +a

case "$ENGINE" in
  postgres)
    cat "$FILE" | docker exec -i -e PGPASSWORD="$POSTGRES_PASSWORD" infra-postgres \
      pg_restore --clean --if-exists --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB"
    ;;
  mysql)
    cat "$FILE" | docker exec -i infra-mysql sh -c \
      "exec mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\""
    ;;
  *) usage ;;
esac

echo "Restauración completada para $ENGINE"
