#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ $# -ne 2 ]]; then
  echo "Uso: $0 usuario@dominio contraseña" >&2
  exit 1
fi

EMAIL="$1"
PASSWORD="$2"

docker compose --env-file .env -f mail/docker-compose.yml run --rm mailserver setup email add "$EMAIL" "$PASSWORD"
docker compose --env-file .env -f mail/docker-compose.yml restart mailserver
