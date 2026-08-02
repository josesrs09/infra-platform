#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRON_FILE="/etc/cron.d/infra-platform-backups"

[[ "${EUID}" -eq 0 ]] || { echo "Ejecuta con sudo." >&2; exit 1; }

cat > "$CRON_FILE" <<EOF
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# Copias lógicas de PostgreSQL y MySQL a las 01:30.
30 1 * * * root cd $ROOT_DIR && $ROOT_DIR/backups/scripts/backup-databases.sh >> $ROOT_DIR/backups/data/database-backup.log 2>&1

# Snapshot Restic de toda la plataforma a las 02:00.
0 2 * * * root cd $ROOT_DIR && docker compose --env-file .env -f backups/docker-compose.yml run --rm restic-backup >> $ROOT_DIR/backups/data/restic-backup.log 2>&1
EOF

chmod 0644 "$CRON_FILE"
command -v systemctl >/dev/null 2>&1 && systemctl restart cron || true

echo "Programación instalada en $CRON_FILE"
