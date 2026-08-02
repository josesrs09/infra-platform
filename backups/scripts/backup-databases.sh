#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "No existe .env" >&2
  exit 1
fi

set -a
source .env
set +a

STAMP="$(date +%Y%m%d_%H%M%S)"
DEST="backups/data/databases/$STAMP"
mkdir -p "$DEST/postgres" "$DEST/mysql"

if docker ps --format '{{.Names}}' | grep -qx infra-postgres; then
  docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" infra-postgres \
    pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc \
    > "$DEST/postgres/${POSTGRES_DB}.dump"

  docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" infra-postgres \
    pg_dumpall -U "$POSTGRES_USER" --globals-only \
    > "$DEST/postgres/globals.sql"
else
  echo "PostgreSQL no está activo; se omite." >&2
fi

if docker ps --format '{{.Names}}' | grep -qx infra-mysql; then
  docker exec infra-mysql sh -c \
    "exec mysqldump -uroot -p\"$MYSQL_ROOT_PASSWORD\" --single-transaction --routines --triggers --events --databases \"$MYSQL_DATABASE\"" \
    > "$DEST/mysql/${MYSQL_DATABASE}.sql"
else
  echo "MySQL no está activo; se omite." >&2
fi

find backups/data/databases -mindepth 1 -maxdepth 1 -type d -mtime +14 -exec rm -rf {} +

echo "Respaldos lógicos creados en $DEST"
