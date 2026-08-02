#!/usr/bin/env bash
set -Eeuo pipefail

MODULE=${1:-}
if [[ -z "$MODULE" || ! -f "$MODULE/docker-compose.yml" ]]; then
  echo "Uso: $0 <modulo>" >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "Falta .env. Ejecute: cp .env.example .env" >&2
  exit 1
fi

docker compose --env-file .env -f "$MODULE/docker-compose.yml" config >/dev/null
docker compose --env-file .env -f "$MODULE/docker-compose.yml" up -d --remove-orphans
