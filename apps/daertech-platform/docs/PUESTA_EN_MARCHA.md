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
sudo chown -R "$USER":"$USER" /opt/infra-platform
git clone https://github.com/josesrs09/infra-platform.git /opt/infra-platform
cd /opt/infra-platform
git checkout feature/daertech-platform-foundation
cd apps/daertech-platform
```

Después de fusionar la rama, usa `main` en lugar de la rama de funcionalidad.

## 3. Crear directorios persistentes

La solución usa bind mounts, no volúmenes nombrados.

```bash
mkdir -p data/postgres data/redis logs backups certificates secrets
chmod 700 data/postgres data/redis secrets certificates
```

No agregues estos directorios a Git.

## 4. Configurar variables

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

Cambia obligatoriamente:

- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`
- `ADMIN_PASSWORD`
- `PUBLIC_FRONTEND_URL`
- `PUBLIC_API_URL`

Genera secretos robustos:

```bash
openssl rand -base64 48
```

El `JWT_SECRET` debe tener al menos 64 caracteres y no debe reutilizar contraseñas.

## 5. Datos del administrador inicial

Configura en `.env`:

```dotenv
ADMIN_NAME=José Rafael Santos Rosario
ADMIN_EMAIL=admin@daertechglobal.com
ADMIN_USERNAME=admin
ADMIN_PASSWORD=UNA_CLAVE_TEMPORAL_MUY_FUERTE
```

La contraseña debe cambiarse inmediatamente después del primer inicio de sesión cuando el módulo de autenticación quede habilitado.

## 6. DNS manual

Crea registros `A` apuntando a la IP pública del VPS:

```text
infra.daertechglobal.com
api-infra.daertechglobal.com
```

Para las consolas de infraestructura, crea según se habiliten:

```text
traefik.daertechglobal.com
grafana.daertechglobal.com
prometheus.daertechglobal.com
logs.daertechglobal.com
alertmanager.daertechglobal.com
rabbitmq.daertechglobal.com
minio.daertechglobal.com
s3.daertechglobal.com
portainer.daertechglobal.com
```

Valida propagación:

```bash
dig +short infra.daertechglobal.com
dig +short api-infra.daertechglobal.com
```

## 7. Traefik y HTTPS

La aplicación puede arrancar inicialmente por puertos locales. Para producción debe conectarse a la red externa de Traefik existente y utilizar certificados Let's Encrypt.

Cambios manuales requeridos en el `docker-compose.yml` de producción:

1. Añadir la red externa de Traefik.
2. Eliminar la publicación pública de los puertos 4200 y 8080.
3. Añadir etiquetas para `infra.daertechglobal.com` y `api-infra.daertechglobal.com`.
4. Confirmar el nombre del `certresolver` configurado en Traefik.

Ejemplo conceptual para frontend:

```yaml
labels:
  - traefik.enable=true
  - traefik.http.routers.daertech-platform.rule=Host(`infra.daertechglobal.com`)
  - traefik.http.routers.daertech-platform.entrypoints=websecure
  - traefik.http.routers.daertech-platform.tls.certresolver=letsencrypt
  - traefik.http.services.daertech-platform.loadbalancer.server.port=80
```

Ejemplo conceptual para backend:

```yaml
labels:
  - traefik.enable=true
  - traefik.http.routers.daertech-api.rule=Host(`api-infra.daertechglobal.com`)
  - traefik.http.routers.daertech-api.entrypoints=websecure
  - traefik.http.routers.daertech-api.tls.certresolver=letsencrypt
  - traefik.http.services.daertech-api.loadbalancer.server.port=8080
```

## 8. Telegram

Crea un bot con BotFather y configura:

```dotenv
TELEGRAM_BOT_TOKEN=valor_real
TELEGRAM_CHAT_ID=valor_real
```

Nunca publiques estos valores en GitHub. La integración funcional se conectará en la fase de alertas.

## 9. Dropbox y Rclone

Instala Rclone en el servidor y crea el remote:

```bash
rclone config
```

Nombre recomendado:

```text
dropbox
```

Ruta remota:

```text
infra-platform/backups
```

Prueba:

```bash
rclone lsd dropbox:
rclone mkdir dropbox:infra-platform/backups
```

El archivo `rclone.conf` debe permanecer fuera de Git y con permisos `600`.

## 10. Construcción y arranque

```bash
docker compose config
docker compose build --pull
docker compose up -d
```

Verifica:

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 frontend
```

## 11. Validaciones de salud

```bash
curl -f http://localhost:8080/api/v1/actuator/health
curl -f http://localhost:8080/api/v1/actuator/info
curl -f http://localhost:8080/api/v1/actuator/prometheus
curl -I http://localhost:4200
```

Resultado esperado: servicios `healthy` y respuestas HTTP 200.

## 12. Base de datos

Flyway crea el esquema `platform` y las tablas iniciales al iniciar el backend.

Valida:

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\dt platform.*'
```

No ejecutes migraciones manualmente en producción salvo procedimiento de recuperación aprobado.

## 13. Firewall

Abre únicamente:

```text
22/tcp o puerto SSH personalizado
80/tcp
443/tcp
```

PostgreSQL, Redis y el backend no deben exponerse a Internet. Si se publican temporalmente para pruebas, restringe el origen por IP y retira la regla al finalizar.

## 14. Cambios manuales pendientes antes de producción

- Confirmar IP pública del VPS.
- Crear todos los registros DNS requeridos.
- Confirmar usuario y puerto SSH.
- Configurar Traefik y su `certresolver` real.
- Configurar secretos en `.env` o Docker secrets.
- Definir política de retención de backups.
- Configurar Rclone con Dropbox.
- Configurar bot y chat de Telegram.
- Configurar SMTP y cuentas de alertas.
- Registrar aplicaciones reales y sus repositorios.
- Confirmar endpoints de health y métricas de cada aplicación.
- Integrar Prometheus, Loki, Grafana y Alertmanager.
- Ejecutar pruebas de recuperación de PostgreSQL y Redis.
- Cambiar la contraseña inicial del administrador.
- Deshabilitar puertos de desarrollo expuestos.
- Activar copias externas y prueba mensual de restauración.

## 15. Operación diaria

```bash
cd /opt/infra-platform/apps/daertech-platform
docker compose ps
docker compose logs --since=30m backend
docker compose pull
docker compose up -d --build
```

Antes de actualizar:

1. Respaldar PostgreSQL.
2. Respaldar `.env` y configuraciones externas.
3. Revisar migraciones.
4. Desplegar en QA.
5. Ejecutar smoke tests.
6. Desplegar en producción.
7. Verificar health, métricas y logs.

## 16. Recuperación básica

PostgreSQL:

```bash
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > backups/daertech-platform-$(date +%F-%H%M).dump
```

Restauración debe realizarse primero en un ambiente aislado:

```bash
docker compose exec -T postgres pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists < backups/ARCHIVO.dump
```

## 17. Criterio de producción

No publiques la plataforma hasta cumplir todos estos puntos:

- Compilación sin errores.
- Migraciones exitosas.
- Health checks correctos.
- HTTPS válido.
- Secretos fuera de Git.
- Firewall restringido.
- Backup y restauración probados.
- Alertas operativas.
- Usuario administrador protegido.
- Revisión de permisos y auditoría.
- Monitoreo de CPU, memoria, disco, contenedores y aplicación.
