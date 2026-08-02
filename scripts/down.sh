#!/usr/bin/env bash
set -Eeuo pipefail

MODULE=${1:-}
if [[ -z "$MODULE" || ! -f "$MODULE/docker-compose.yml" ]]; then
  echo "Uso: $0 <modulo>" >&2
  exit 1
fi

docker compose --env-file .env -f "$MODULE/docker-compose.yml" down
