# Implementación completa en producción — `daertechglobal.com`

Esta guía describe el despliegue de `infra-platform` en Debian utilizando el dominio oficial `daertechglobal.com`.

Para la preparación detallada del sistema operativo, firewall, Docker, DNS y secretos consulte también [`CONFIGURACION_VPS_DAERTECHGLOBAL.md`](CONFIGURACION_VPS_DAERTECHGLOBAL.md).

## 1. Requisitos del servidor

- Debian 12 o superior.
- 8 CPU, 16 GB RAM y 300 GB SSD como base.
- 12–16 CPU, 32 GB RAM y 500 GB SSD recomendados para todos los módulos.
- IP pública fija.
- DNS administrable.
- PTR/rDNS de la IP hacia `mail.daertechglobal.com` si se habilita correo.
- Docker Engine y Docker Compose v2.
- Git, curl, jq, apache2-utils, rclone y Restic.

```bash
sudo apt update
sudo apt install -y git curl jq apache2-utils rclone restic ca-certificates gnupg
```

## 2. Clonar y preparar

```bash
sudo mkdir -p /opt/infra-platform
sudo chown "$USER":"$USER" /opt/infra-platform
git clone https://github.com/josesrs09/infra-platform.git /opt/infra-platform
cd /opt/infra-platform
git checkout feature/daertech-platform-foundation
cp .env.example .env
chmod 600 .env
sudo ./scripts/bootstrap.sh
```

Para producción debe utilizarse un tag o commit aprobado y registrar el SHA:

```bash
git rev-parse HEAD
```

## 3. Dominio principal

Configure en `/opt/infra-platform/.env`:

```dotenv
DOMAIN=daertechglobal.com
ACME_EMAIL=infraestructura@daertechglobal.com
```

## 4. Subdominios oficiales

Cree registros DNS tipo `A` hacia la IP pública del VPS:

```text
infra.daertechglobal.com
traefik.daertechglobal.com
portainer.daertechglobal.com
uptime.daertechglobal.com
prometheus.daertechglobal.com
grafana.daertechglobal.com
logs.daertechglobal.com
s3.daertechglobal.com
minio.daertechglobal.com
mqtt.daertechglobal.com
rabbitmq.daertechglobal.com
registry.daertechglobal.com
git.daertechglobal.com
pgadmin.daertechglobal.com
phpmyadmin.daertechglobal.com
redis.daertechglobal.com
adminer.daertechglobal.com
mail.daertechglobal.com
api-infra.daertechglobal.com
```

Variables correspondientes:

```dotenv
TRAEFIK_DASHBOARD_HOST=traefik.daertechglobal.com
HOMEPAGE_HOST=infra.daertechglobal.com
PORTAINER_HOST=portainer.daertechglobal.com
UPTIME_HOST=uptime.daertechglobal.com
PROMETHEUS_HOST=prometheus.daertechglobal.com
GRAFANA_HOST=grafana.daertechglobal.com
DOZZLE_HOST=logs.daertechglobal.com
MINIO_HOST=s3.daertechglobal.com
MINIO_CONSOLE_HOST=minio.daertechglobal.com
EMQX_HOST=mqtt.daertechglobal.com
RABBITMQ_HOST=rabbitmq.daertechglobal.com
REGISTRY_HOST=registry.daertechglobal.com
GITEA_HOST=git.daertechglobal.com
PGADMIN_HOST=pgadmin.daertechglobal.com
PHPMYADMIN_HOST=phpmyadmin.daertechglobal.com
REDIS_COMMANDER_HOST=redis.daertechglobal.com
ADMINER_HOST=adminer.daertechglobal.com
```

## 5. Autenticación de Traefik

Genere un hash bcrypt:

```bash
htpasswd -nbB admin 'CONTRASENA_LARGA_Y_UNICA'
```

Guárdelo en:

```dotenv
TRAEFIK_DASHBOARD_AUTH=admin:HASH_GENERADO
```

Escape cada `$` como `$$` cuando el valor se utilice directamente dentro de etiquetas Compose.

## 6. Credenciales de bases de datos

Use contraseñas independientes:

```dotenv
POSTGRES_DB=platform
POSTGRES_USER=platform_admin
POSTGRES_PASSWORD=<SECRETO_POSTGRES>
MYSQL_DATABASE=platform
MYSQL_USER=platform_admin
MYSQL_PASSWORD=<SECRETO_MYSQL>
MYSQL_ROOT_PASSWORD=<SECRETO_MYSQL_ROOT>
REDIS_PASSWORD=<SECRETO_REDIS>
```

No reutilice contraseñas administrativas.

## 7. Servicios web

Configure al menos:

```dotenv
GRAFANA_ADMIN_PASSWORD=<SECRETO_GRAFANA>
PGADMIN_EMAIL=infraestructura@daertechglobal.com
PGADMIN_PASSWORD=<SECRETO_PGADMIN>
REDIS_COMMANDER_PASSWORD=<SECRETO_REDIS_COMMANDER>
MINIO_ROOT_PASSWORD=<SECRETO_MINIO>
EMQX_DASHBOARD_PASSWORD=<SECRETO_EMQX>
RABBITMQ_PASSWORD=<SECRETO_RABBITMQ>
GITEA_DB_PASSWORD=<SECRETO_GITEA>
RESTIC_PASSWORD=<SECRETO_RESTIC>
```

## 8. Telegram

```dotenv
TELEGRAM_BOT_TOKEN=<TOKEN_REAL>
TELEGRAM_CHAT_ID=<ID_REAL>
```

Prueba:

```bash
curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  -d chat_id="${TELEGRAM_CHAT_ID}" \
  --data-urlencode text="Prueba infra-platform daertechglobal.com"
```

## 9. Dropbox con Rclone

```bash
rclone config
```

Guarde la configuración en:

```text
/opt/infra-platform/backups/rclone/rclone.conf
```

Variables:

```dotenv
RCLONE_DROPBOX_REMOTE=dropbox
RCLONE_DROPBOX_PATH=infra-platform/backups
```

Prueba:

```bash
rclone --config backups/rclone/rclone.conf lsd dropbox:
```

## 10. Correo

Configure:

```dotenv
MAIL_HOSTNAME=mail
MAIL_DOMAIN=daertechglobal.com
POSTMASTER_ADDRESS=postmaster@daertechglobal.com
```

DNS requerido:

- `A`: `mail.daertechglobal.com` hacia la IP pública.
- `MX`: `daertechglobal.com` hacia `mail.daertechglobal.com`.
- `PTR/rDNS`: IP pública hacia `mail.daertechglobal.com`.
- SPF autorizando la IP y el host MX.
- DKIM generado por el servidor de correo.
- DMARC inicialmente en modo de monitoreo y después `quarantine` o `reject`.

Cree una cuenta:

```bash
./mail/scripts/add-account.sh usuario@daertechglobal.com 'CONTRASENA_REAL'
```

## 11. Validación integral

```bash
cd /opt/infra-platform
./scripts/validate.sh
```

La validación debe revisar:

- todos los archivos `docker-compose.yml`;
- sintaxis Bash;
- JSON y YAML;
- variables obligatorias;
- credenciales débiles o valores `CHANGE_ME`;
- persistencia mediante carpetas locales.

No continúe a producción mientras exista un resultado `FAIL`.

## 12. Orden de despliegue

```bash
./scripts/bootstrap.sh

docker compose --env-file .env -f proxy/docker-compose.yml up -d
docker compose --env-file .env -f databases/docker-compose.yml up -d
docker compose --env-file .env -f messaging/docker-compose.yml up -d
docker compose --env-file .env -f storage/docker-compose.yml up -d
docker compose --env-file .env -f monitoring/docker-compose.yml up -d
docker compose --env-file .env -f logging/docker-compose.yml up -d
docker compose --env-file .env -f security/docker-compose.yml up -d
docker compose --env-file .env -f management/docker-compose.yml up -d
docker compose --env-file .env -f ci-cd/docker-compose.yml up -d
docker compose --env-file .env -f mail/docker-compose.yml up -d
```

Verifique después de cada módulo:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

## 13. Instrumentar aplicaciones

1. Exponer `/health` y `/metrics`.
2. Usar las métricas documentadas en `examples/http-metrics/README.md`.
3. Conectar el contenedor a `infra_monitoring`.
4. Registrar el target en `monitoring/prometheus/targets/applications.yml`.
5. Recargar Prometheus.

```bash
curl -X POST http://localhost:9090/-/reload
```

```yaml
networks:
  monitoring:
    external: true
    name: infra_monitoring
```

## 14. Reportar errores por RabbitMQ

Exchange:

```text
app.errors
```

Routing keys:

```text
nombre-app.critical
nombre-app.warning
nombre-app.info
```

Payload recomendado:

```json
{
  "application": "api-facturacion",
  "environment": "production",
  "severity": "critical",
  "timestamp": "2026-08-03T10:00:00-04:00",
  "message": "Error procesando factura",
  "correlationId": "factura-123",
  "exception": "TimeoutException",
  "endpoint": "/api/facturas",
  "statusCode": 500
}
```

## 15. Backups y Dropbox

```bash
./backups/scripts/backup-databases.sh
./backups/scripts/upload-dropbox.sh
sudo ./scripts/install-backup-cron.sh
```

Compruebe:

```bash
cat /etc/cron.d/infra-platform-backups
tail -f backups/data/database-backup.log
tail -f backups/data/dropbox-upload.log
```

## 16. Pruebas posteriores

- Abrir `https://infra.daertechglobal.com`.
- Validar `https://traefik.daertechglobal.com` con autenticación.
- Validar `https://grafana.daertechglobal.com`.
- Validar Telegram.
- Detener una aplicación registrada y confirmar alerta.
- Publicar un evento `critical` en RabbitMQ.
- Generar una línea `ERROR` y comprobar Loki.
- Ejecutar backup manual y validar Dropbox.
- Restaurar una base en un ambiente aislado.

## 17. Migración a otro VPS

1. Detener escrituras.
2. Ejecutar dumps y Restic.
3. Subir copia a Dropbox.
4. Clonar el mismo tag o commit en el nuevo VPS.
5. Transferir `.env`, `rclone.conf` y secretos por canal seguro.
6. Restaurar carpetas persistentes.
7. Restaurar PostgreSQL y MySQL.
8. Cambiar los registros DNS hacia la nueva IP.
9. Actualizar PTR/rDNS si se mueve el correo.
10. Validar certificados, correo, Telegram, métricas y alertas.
11. Mantener el VPS anterior disponible durante la ventana de reversión.

Nunca copie una base activa a nivel de archivos sin detenerla o sin utilizar un snapshot consistente.

## 18. Checklist de producción

- [ ] DNS de `daertechglobal.com` configurado.
- [ ] PTR/rDNS correcto para `mail.daertechglobal.com`.
- [ ] `.env` sin `CHANGE_ME`.
- [ ] Hash bcrypt real en Traefik.
- [ ] Telegram probado.
- [ ] Dropbox probado.
- [ ] Backup y restauración probados.
- [ ] Todas las consolas con HTTPS.
- [ ] RabbitMQ sin usuario `guest` remoto.
- [ ] PostgreSQL, MySQL y Redis no publicados a Internet.
- [ ] Métricas por aplicación visibles.
- [ ] Alertas HTTP, logs y colas probadas.
- [ ] `./scripts/validate.sh` sin fallos.
- [ ] Procedimiento de reversión documentado.
