#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

[[ -f .env ]] || { echo "No existe .env" >&2; exit 1; }
set -a
source .env
set +a

STAMP="$(date +%Y%m%d_%H%M%S)"
START_EPOCH="$(date +%s)"
DEST="backups/data/databases/$STAMP"
METRICS_DIR="monitoring/textfile"
METRICS_TMP="$METRICS_DIR/backup.prom.tmp"
METRICS_FILE="$METRICS_DIR/backup.prom"
mkdir -p "$DEST/postgres" "$DEST/mysql" "$METRICS_DIR"

notify() { backups/scripts/notify-telegram.sh "$1" || true; }
write_metrics() {
  local status="$1" end_epoch duration size
  end_epoch="$(date +%s)"
  duration="$((end_epoch - START_EPOCH))"
  size="$(du -sb "$DEST" 2>/dev/null | awk '{print $1}' || echo 0)"
  cat > "$METRICS_TMP" <<EOF
infra_backup_last_status{type="database"} $status
infra_backup_last_start_timestamp_seconds{type="database"} $START_EPOCH
infra_backup_last_finish_timestamp_seconds{type="database"} $end_epoch
infra_backup_last_duration_seconds{type="database"} $duration
infra_backup_last_size_bytes{type="database"} $size
EOF
  mv "$METRICS_TMP" "$METRICS_FILE"
}

on_error() {
  local code=$?
  write_metrics 0
  notify "❌ <b>Backup de bases de datos falló</b>%0AHost: $(hostname)%0AFecha: $(date -Is)%0ACódigo: $code"
  exit "$code"
}
trap on_error ERR

notify "▶️ <b>Iniciando backup de bases de datos</b>%0AHost: $(hostname)%0AFecha: $(date -Is)"

if docker ps --format '{{.Names}}' | grep -qx infra-postgres; then
  docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" infra-postgres \
    pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc \
    > "$DEST/postgres/${POSTGRES_DB}.dump"
  docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" infra-postgres \
    pg_dumpall -U "$POSTGRES_USER" --globals-only \
    > "$DEST/postgres/globals.sql"
else
  echo "PostgreSQL no está activo" >&2
  false
fi

if docker ps --format '{{.Names}}' | grep -qx infra-mysql; then
  docker exec infra-mysql sh -c \
    "exec mysqldump -uroot -p\"$MYSQL_ROOT_PASSWORD\" --single-transaction --routines --triggers --events --databases \"$MYSQL_DATABASE\"" \
    > "$DEST/mysql/${MYSQL_DATABASE}.sql"
else
  echo "MySQL no está activo" >&2
  false
fi

find backups/data/databases -mindepth 1 -maxdepth 1 -type d -mtime +14 -exec rm -rf {} +
write_metrics 1
SIZE_HUMAN="$(du -sh "$DEST" | awk '{print $1}')"
notify "✅ <b>Backup de bases de datos finalizado</b>%0AHost: $(hostname)%0AFecha: $(date -Is)%0ATamaño: $SIZE_HUMAN%0ARuta: $DEST"
echo "Respaldos lógicos creados en $DEST"
