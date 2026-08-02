#!/usr/bin/env bash
set -Eeuo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker no está instalado." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose Plugin no está disponible." >&2
  exit 1
fi

for network in infra_proxy infra_backend infra_database infra_monitoring infra_logging infra_messaging infra_storage infra_security; do
  if ! docker network inspect "$network" >/dev/null 2>&1; then
    docker network create "$network"
  fi
done

mkdir -p proxy/letsencrypt proxy/logs secrets backups/data
install -m 600 /dev/null proxy/letsencrypt/acme.json

echo "Infraestructura base preparada."
