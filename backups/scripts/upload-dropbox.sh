#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
[[ -f .env ]] || { echo "No existe .env" >&2; exit 1; }
set -a
source .env
set +a

: "${RCLONE_DROPBOX_REMOTE:=dropbox}"
: "${RCLONE_DROPBOX_PATH:=infra-platform/backups}"
START_EPOCH="$(date +%s)"
METRICS_DIR="monitoring/textfile"
METRICS_TMP="$METRICS_DIR/dropbox.prom.tmp"
METRICS_FILE="$METRICS_DIR/dropbox.prom"
mkdir -p "$METRICS_DIR"

notify() { backups/scripts/notify-telegram.sh "$1" || true; }
write_metrics() {
  local status="$1" end_epoch duration
  end_epoch="$(date +%s)"
  duration="$((end_epoch - START_EPOCH))"
  cat > "$METRICS_TMP" <<EOF
infra_dropbox_upload_last_status $status
infra_dropbox_upload_last_start_timestamp_seconds $START_EPOCH
infra_dropbox_upload_last_finish_timestamp_seconds $end_epoch
infra_dropbox_upload_last_duration_seconds $duration
EOF
  mv "$METRICS_TMP" "$METRICS_FILE"
}

on_error() {
  local code=$?
  write_metrics 0
  notify "❌ <b>Carga a Dropbox falló</b>%0AHost: $(hostname)%0AFecha: $(date -Is)%0ACódigo: $code"
  exit "$code"
}
trap on_error ERR

command -v rclone >/dev/null 2>&1 || { echo "rclone no está instalado" >&2; false; }
[[ -f backups/rclone/rclone.conf ]] || { echo "Falta backups/rclone/rclone.conf" >&2; false; }

notify "☁️ <b>Iniciando carga de backups a Dropbox</b>%0AHost: $(hostname)%0AFecha: $(date -Is)"
rclone sync backups/data "${RCLONE_DROPBOX_REMOTE}:${RCLONE_DROPBOX_PATH}/data" \
  --config backups/rclone/rclone.conf --create-empty-src-dirs --checksum --transfers 4 --checkers 8
rclone sync backups/repository "${RCLONE_DROPBOX_REMOTE}:${RCLONE_DROPBOX_PATH}/restic" \
  --config backups/rclone/rclone.conf --create-empty-src-dirs --checksum --transfers 4 --checkers 8
write_metrics 1
notify "✅ <b>Carga a Dropbox finalizada</b>%0AHost: $(hostname)%0AFecha: $(date -Is)%0ADestino: ${RCLONE_DROPBOX_REMOTE}:${RCLONE_DROPBOX_PATH}"
