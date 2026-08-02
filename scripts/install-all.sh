#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./scripts/bootstrap.sh

MODULES=(
  proxy
  management
  databases
  storage
  messaging
  monitoring
  logging
  security
  mail
  ci-cd
)

for module in "${MODULES[@]}"; do
  echo "==> Iniciando $module"
  docker compose --env-file .env -f "$module/docker-compose.yml" up -d
  echo
 done

echo "Todos los módulos configurados fueron iniciados."
