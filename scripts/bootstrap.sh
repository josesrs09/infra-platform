#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker no está instalado." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose Plugin no está disponible." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Se creó .env. Ajusta dominios y credenciales antes de publicar servicios."
fi

for network in infra_proxy infra_backend infra_database infra_monitoring infra_logging infra_messaging infra_storage infra_security; do
  docker network inspect "$network" >/dev/null 2>&1 || docker network create "$network"
done

mkdir -p \
  proxy/letsencrypt proxy/logs \
  monitoring/prometheus/rules monitoring/grafana/provisioning/datasources \
  logging/loki logging/alloy \
  backups/repository backups/cache backups/data \
  secrets

if [[ ! -f proxy/letsencrypt/acme.json ]]; then
  install -m 600 /dev/null proxy/letsencrypt/acme.json
else
  chmod 600 proxy/letsencrypt/acme.json
fi

chmod +x scripts/*.sh backups/scripts/*.sh 2>/dev/null || true

echo "Infraestructura base preparada."
