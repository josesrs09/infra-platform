# Implementación completa en producción

Esta guía describe el despliegue de `infra-platform` en un servidor Debian, la sustitución de datos de ejemplo por valores reales, la validación y las pruebas operativas.

## 1. Requisitos del servidor

Recomendado para una instalación inicial:

- Debian 12 o superior.
- 8 CPU, 16 GB RAM y 300 GB SSD como base.
- IP pública fija.
- DNS administrable.
- Puertos 22, 25, 80, 443, 465, 587, 993, 1883, 2222, 5672 y 8883 según los servicios habilitados.
- PTR/rDNS del IP apuntando al host de correo.

Instale Docker Engine, Docker Compose v2, Git, curl, jq, apache2-utils, rclone y Restic.

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
cp .env.example .env
sudo ./scripts/bootstrap.sh
```

## 3. Sustitución de datos reales

Edite `/opt/infra-platform/.env`. Nunca publique este archivo en Git.

### 3.1 Dominio principal

Cambie:

```env
DOMAIN=example.com
ACME_EMAIL=admin@example.com
```

Por valores reales, por ejemplo:

```env
DOMAIN=daertechglobal.com
ACME_EMAIL=infraestructura@daertechglobal.com
```

### 3.2 Subdominios

Cree registros DNS tipo A hacia la IP pública:

```text
infra.dominio.com
traefik.dominio.com
portainer.dominio.com
uptime.dominio.com
prometheus.dominio.com
grafana.dominio.com
logs.dominio.com
s3.dominio.com
minio.dominio.com
mqtt.dominio.com
rabbitmq.dominio.com
registry.dominio.com
git.dominio.com
pgadmin.dominio.com
phpmyadmin.dominio.com
redis.dominio.com
adminer.dominio.com
mail.dominio.com
```

Después reemplace todos los hosts `*.example.com` en `.env`.

### 3.3 Contraseña general de Traefik

Genere un hash bcrypt:

```bash
htpasswd -nbB admin 'CONTRASENA_LARGA_Y_UNICA'
```

Escape cada `$` como `$$` cuando el valor se use directamente en etiquetas Compose. Guarde el resultado en:

```env
TRAEFIK_DASHBOARD_AUTH=admin:HASH_GENERADO
```

Actualice también `proxy/dynamic/middlewares.yml` con el hash real o use el script de renderización si está habilitado.

### 3.4 Credenciales de bases de datos

Use contraseñas independientes:

```env
POSTGRES_DB=platform
POSTGRES_USER=platform_admin
POSTGRES_PASSWORD=VALOR_REAL
MYSQL_DATABASE=platform
MYSQL_USER=platform_admin
MYSQL_PASSWORD=VALOR_REAL
MYSQL_ROOT_PASSWORD=VALOR_REAL_DISTINTO
REDIS_PASSWORD=VALOR_REAL
```

No reutilice la contraseña del administrador web.

### 3.5 Servicios web

Cambie como mínimo:

```env
GRAFANA_ADMIN_PASSWORD=VALOR_REAL
PGADMIN_EMAIL=infraestructura@dominio.com
PGADMIN_PASSWORD=VALOR_REAL
REDIS_COMMANDER_PASSWORD=VALOR_REAL
MINIO_ROOT_PASSWORD=VALOR_REAL
EMQX_DASHBOARD_PASSWORD=VALOR_REAL
RABBITMQ_PASSWORD=VALOR_REAL
GITEA_DB_PASSWORD=VALOR_REAL
RESTIC_PASSWORD=VALOR_REAL
```

### 3.6 Telegram

Cree el bot con BotFather, agregue el bot al chat y configure:

```env
TELEGRAM_BOT_TOKEN=TOKEN_REAL
TELEGRAM_CHAT_ID=ID_REAL
```

Prueba directa:

```bash
curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  -d chat_id="${TELEGRAM_CHAT_ID}" \
  --data-urlencode text="Prueba infra-platform"
```

### 3.7 Dropbox con rclone

Configure el remoto:

```bash
rclone config
```

Seleccione Dropbox y guarde el archivo en:

```text
/opt/infra-platform/backups/rclone/rclone.conf
```

Variables:

```env
RCLONE_DROPBOX_REMOTE=dropbox
RCLONE_DROPBOX_PATH=infra-platform/backups
```

Prueba:

```bash
rclone --config backups/rclone/rclone.conf lsd dropbox:
```

### 3.8 Correo

Configure:

```env
MAIL_HOSTNAME=mail
MAIL_DOMAIN=dominio.com
POSTMASTER_ADDRESS=postmaster@dominio.com
```

DNS requerido:

- A: `mail.dominio.com` hacia la IP.
- MX: dominio hacia `mail.dominio.com`.
- PTR/rDNS: IP hacia `mail.dominio.com`.
- SPF, DKIM y DMARC después de generar DKIM.

Cree una cuenta:

```bash
./mail/scripts/add-account.sh usuario@dominio.com 'CONTRASENA_REAL'
```

## 4. Validación integral

Ejecute:

```bash
cd /opt/infra-platform
./scripts/validate.sh
```

La validación revisa:

- Todos los `docker-compose.yml`.
- Sintaxis Bash.
- JSON y YAML.
- Variables obligatorias.
- Posibles credenciales débiles.
- Uso exclusivo de carpetas locales para persistencia.

No continúe a producción mientras exista un resultado `FAIL`.

## 5. Orden de despliegue

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

Verifique:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

## 6. Instrumentar una aplicación

1. Exponga `/health` y `/metrics`.
2. Use las métricas y etiquetas documentadas en `examples/http-metrics/README.md`.
3. Conecte el contenedor a `infra_monitoring`.
4. Registre el target en `monitoring/prometheus/targets/applications.yml`.
5. Recargue Prometheus:

```bash
curl -X POST http://localhost:9090/-/reload
```

Ejemplo de red Compose:

```yaml
networks:
  monitoring:
    external: true
    name: infra_monitoring
```

## 7. Reportar errores por RabbitMQ

Publique en el exchange `app.errors`.

Routing keys:

```text
nombre-app.critical
nombre-app.warning
nombre-app.info
```

Mensaje JSON recomendado:

```json
{
  "application": "api-facturacion",
  "environment": "production",
  "severity": "critical",
  "timestamp": "2026-08-02T14:00:00-04:00",
  "message": "Error procesando factura",
  "correlationId": "factura-123",
  "exception": "TimeoutException",
  "endpoint": "/api/facturas",
  "statusCode": 500
}
```

El puente `error-telegram-bridge` reenvía los eventos a Telegram.

## 8. Backups y Dropbox

Prueba manual:

```bash
./backups/scripts/backup-databases.sh
./backups/scripts/upload-dropbox.sh
```

Instale programación:

```bash
sudo ./scripts/install-backup-cron.sh
```

Compruebe:

```bash
cat /etc/cron.d/infra-platform-backups
tail -f backups/data/database-backup.log
tail -f backups/data/dropbox-upload.log
```

Debe recibir alertas Telegram al iniciar, finalizar o fallar cada proceso.

## 9. Pruebas posteriores

### Telegram

- Simule un HTTP 500 varias veces.
- Detenga una aplicación registrada.
- Publique un mensaje `critical` en RabbitMQ.
- Genere una línea `ERROR` en logs.
- Ejecute un backup manual.

### Métricas

En Prometheus compruebe:

```promql
sum by (application) (rate(http_requests_total[5m]))
```

```promql
sum by (application) (rate(http_requests_total{status_code=~"5.."}[5m]))
```

```promql
histogram_quantile(0.95, sum by (le, application) (rate(http_request_duration_seconds_bucket[5m])))
```

## 10. Migración y cambios de datos

Para mover la instalación a otro VPS:

1. Detenga escrituras de aplicaciones.
2. Ejecute backup lógico y Restic.
3. Cargue a Dropbox.
4. Clone el repositorio en el nuevo VPS.
5. Copie `.env`, `backups/rclone/rclone.conf` y secretos por canal seguro.
6. Restaure las carpetas locales o use Restic.
7. Restaure PostgreSQL/MySQL con `restore-database.sh`.
8. Cambie DNS hacia la nueva IP.
9. Compruebe certificados, correo y Telegram.
10. Mantenga el servidor anterior detenido pero disponible durante la ventana de reversión.

Nunca copie una base activa a nivel de archivos sin detenerla o sin usar un mecanismo consistente de snapshot.

## 11. Checklist de producción

- [ ] DNS y PTR correctos.
- [ ] `.env` sin `CHANGE_ME` ni `example.com`.
- [ ] Hash bcrypt real en Traefik.
- [ ] Telegram probado.
- [ ] Dropbox probado.
- [ ] Backup y restauración probados.
- [ ] Todas las consolas con HTTPS.
- [ ] RabbitMQ sin usuario guest.
- [ ] Bases de datos no publicadas directamente a Internet.
- [ ] Métricas por aplicación visibles.
- [ ] Alertas HTTP, logs y colas probadas.
- [ ] `./scripts/validate.sh` sin fallos.
- [ ] Evidencia de prueba y procedimiento de reversión documentados.
