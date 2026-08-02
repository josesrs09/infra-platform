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
  management/data/portainer management/data/uptime-kuma \
  databases/data/postgres databases/data/mysql databases/data/redis \
  databases/init/postgres databases/init/mysql \
  monitoring/data/prometheus monitoring/data/grafana monitoring/data/alertmanager \
  monitoring/prometheus/rules monitoring/grafana/provisioning/datasources \
  logging/data/loki logging/data/alloy logging/loki logging/alloy \
  storage/data/minio \
  messaging/data/emqx messaging/logs/emqx messaging/data/rabbitmq \
  backups/repository backups/cache backups/data \
  secrets

if [[ ! -f proxy/letsencrypt/acme.json ]]; then
  install -m 600 /dev/null proxy/letsencrypt/acme.json
else
  chmod 600 proxy/letsencrypt/acme.json
fi

# Permisos requeridos por imágenes que ejecutan con usuarios no root.
if [[ "${EUID}" -eq 0 ]]; then
  chown -R 999:999 databases/data/postgres databases/data/mysql messaging/data/rabbitmq || true
  chown -R 472:472 monitoring/data/grafana || true
  chown -R 65534:65534 monitoring/data/prometheus monitoring/data/alertmanager || true
  chown -R 10001:10001 logging/data/loki || true
  chown -R 1000:1000 storage/data/minio messaging/data/emqx messaging/logs/emqx || true
else
  echo "Aviso: ejecuta con sudo si algún contenedor reporta permisos denegados en carpetas data/."
fi

chmod +x scripts/*.sh backups/scripts/*.sh 2>/dev/null || true

echo "Infraestructura base y carpetas persistentes preparadas."
