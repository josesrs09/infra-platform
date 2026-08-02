# Puesta en marcha de DAERTECH Platform

Este documento concentra los cambios manuales, secretos, DNS, certificados, rutas y validaciones necesarias para instalar DAERTECH Platform.

## 1. Requisitos del servidor

- Debian 12 o Ubuntu Server 24.04 LTS.
- 4 vCPU mínimo; 8 vCPU recomendado.
- 8 GB RAM mínimo; 16 GB recomendado.
- 120 GB SSD mínimo.
- Docker Engine 27 o superior.
- Docker Compose Plugin.
- Git.
- DNS administrable para `daertechglobal.com`.
- Puertos 80 y 443 disponibles.

## 2. Clonar el repositorio

```bash
sudo mkdir -p /opt/infra-platform
sudo chown "$USER":"$USER" /opt/infra-platform
git clone https://github.com/josesrs09/infra-platform.git /opt/infra-platform
cd /opt/infra-platform/apps/daertech-platform
cp .env.example .env
```

## 3. Directorios persistentes

```bash
sudo mkdir -p /srv/daertech-platform/{postgres,redis,logs,exports}
sudo chown -R 999:999 /srv/daertech-platform/postgres
sudo chown -R 999:999 /srv/daertech-platform/redis
```

La solución usa bind mounts. No sustituya estas rutas por volúmenes nombrados sin documentar y probar una migración.

## 4. Secretos y administrador inicial

Genere secretos distintos:

```bash
openssl rand -base64 64
openssl rand -base64 36
openssl rand -base64 24
```

Configure en `.env`:

```dotenv
APP_JWT_SECRET=<mínimo 64 caracteres>
APP_ADMIN_NAME=José Rafael Santos Rosario
APP_ADMIN_EMAIL=admin@daertechglobal.com
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=<mínimo 12 caracteres>
POSTGRES_PASSWORD=<secreto fuerte>
REDIS_PASSWORD=<secreto fuerte>
```

Nunca cargue `.env`, tokens, certificados privados o `rclone.conf` en GitHub.

## 5. DNS, Traefik y HTTPS

Cree registros `A` hacia la IP pública para:

- `infra.daertechglobal.com`
- `api-infra.daertechglobal.com`

Configure Traefik para emitir certificados Let's Encrypt, redirigir HTTP a HTTPS y conservar `X-Forwarded-For` y `X-Correlation-Id`.

## 6. Arranque

```bash
docker compose config
docker compose build --no-cache
docker compose up -d
docker compose ps
docker compose logs -f backend
```

## 7. Validaciones

```bash
curl -fsS http://127.0.0.1:8080/api/v1/actuator/health
curl -fsS http://127.0.0.1:8080/api/v1/actuator/info
```

Inicie sesión y utilice el access token para validar `/api/v1/admin/users`, `/roles` y `/permissions`. Consulte `FASE_1_SEGURIDAD.md` para los endpoints y comandos.

## 8. Seguridad manual

- Permita 80/443 públicamente y SSH solamente desde IPs administrativas.
- No publique PostgreSQL, Redis ni Actuator directamente a Internet.
- Cambie la contraseña inicial después del primer acceso.
- Restrinja Prometheus a la red de monitoreo.
- Revise `platform.audit_events` para confirmar la auditoría administrativa.
- La rotación de `APP_JWT_SECRET` invalida access tokens existentes.

## 9. Telegram y Dropbox

Configure `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID` únicamente en el servidor. Cree el remote `dropbox` mediante `rclone config`, proteja `rclone.conf` con permisos `600` y pruebe:

```bash
rclone lsd dropbox:
```

## 10. Backups

```bash
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" > /srv/daertech-platform/exports/platform-$(date +%F-%H%M).dump
rclone copy /srv/daertech-platform/exports dropbox:infra-platform/backups
```

Pruebe periódicamente una restauración en un entorno aislado.

## 11. Checklist

- [ ] DNS y HTTPS válidos.
- [ ] `.env` fuera de Git.
- [ ] Secretos fuertes y únicos.
- [ ] Administrador inicial creado.
- [ ] Login y refresh token probados.
- [ ] CRUD de usuarios y roles validado con RBAC.
- [ ] Auditoría administrativa registrada.
- [ ] PostgreSQL y Redis no publicados.
- [ ] Health checks correctos.
- [ ] Backup y restauración probados.
- [ ] Telegram y Dropbox validados.
- [ ] Firewall y SSH endurecidos.
