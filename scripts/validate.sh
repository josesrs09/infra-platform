#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0
warnings=0

ok() { printf 'OK   %s\n' "$1"; }
warn() { printf 'WARN %s\n' "$1"; warnings=$((warnings + 1)); }
fail() { printf 'FAIL %s\n' "$1"; failures=$((failures + 1)); }

command -v docker >/dev/null 2>&1 || { echo "Docker no está instalado." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 no está disponible." >&2; exit 1; }

if [[ ! -f .env ]]; then
  cp .env.example .env
  warn "Se creó .env temporal desde .env.example"
fi

printf '\n== Docker Compose ==\n'
while IFS= read -r compose_file; do
  if docker compose --env-file .env -f "$compose_file" config --quiet >/dev/null; then
    ok "$compose_file"
  else
    fail "$compose_file"
  fi
done < <(find . -mindepth 2 -maxdepth 3 -name docker-compose.yml -print | sort)

printf '\n== Bash ==\n'
while IFS= read -r script; do
  if bash -n "$script"; then
    ok "$script"
  else
    fail "$script"
  fi
done < <(find scripts backups mail messaging -type f -name '*.sh' 2>/dev/null | sort)

printf '\n== JSON ==\n'
while IFS= read -r file; do
  if python3 -m json.tool "$file" >/dev/null; then
    ok "$file"
  else
    fail "$file"
  fi
done < <(find . -type f -name '*.json' ! -path './.git/*' | sort)

printf '\n== YAML básico ==\n'
if command -v ruby >/dev/null 2>&1; then
  while IFS= read -r file; do
    if ruby -e 'require "yaml"; YAML.load_file(ARGV[0])' "$file" >/dev/null 2>&1; then
      ok "$file"
    else
      fail "$file"
    fi
  done < <(find . -type f \( -name '*.yml' -o -name '*.yaml' \) ! -path './.git/*' | sort)
else
  warn "Ruby no disponible; se omite validación YAML adicional"
fi

printf '\n== Variables y secretos ==\n'
required_vars=(
  DOMAIN ACME_EMAIL TRAEFIK_DASHBOARD_AUTH
  POSTGRES_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD
  GRAFANA_ADMIN_PASSWORD MINIO_ROOT_PASSWORD
  RABBITMQ_PASSWORD TELEGRAM_BOT_TOKEN TELEGRAM_CHAT_ID
  RESTIC_PASSWORD
)
for var in "${required_vars[@]}"; do
  if ! grep -qE "^${var}=" .env.example; then
    fail "Falta ${var} en .env.example"
  fi
done

if grep -RInE '(password|token|secret)[[:space:]]*[:=][[:space:]]*["'"']?(admin|password|123456|changeme)["'"']?$' \
  --exclude='.env' --exclude='.env.example' --exclude-dir=.git . >/tmp/infra-secret-scan.txt 2>/dev/null; then
  warn "Se detectaron posibles credenciales débiles; revisar /tmp/infra-secret-scan.txt"
else
  ok "Sin credenciales débiles evidentes en archivos versionados"
fi

printf '\n== Persistencia local ==\n'
if grep -RInE '^[[:space:]]+[A-Za-z0-9_.-]+_data:[[:space:]]*$' --include='docker-compose.yml' . >/tmp/infra-named-volumes.txt; then
  fail "Se detectaron volúmenes Docker nombrados; revisar /tmp/infra-named-volumes.txt"
else
  ok "Servicios persistentes usan bind mounts locales"
fi

printf '\n== Resultado ==\n'
printf 'Fallos: %d | Advertencias: %d\n' "$failures" "$warnings"
[[ "$failures" -eq 0 ]]
