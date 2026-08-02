#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  cp .env.example .env
fi

status=0
while IFS= read -r compose_file; do
  echo "Validando $compose_file"
  if ! docker compose --env-file .env -f "$compose_file" config --quiet; then
    status=1
  fi
done < <(find . -mindepth 2 -maxdepth 2 -name docker-compose.yml -print | sort)

exit "$status"
