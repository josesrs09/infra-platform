# infra-platform — Manual integral de implementación

**Repositorio:** `josesrs09/infra-platform`  
**Rama documentada:** `feature/daertech-platform-foundation`  
**Sistema objetivo:** Debian 12 o superior  
**Modelo:** Docker Engine + Docker Compose v2  
**Zona horaria:** `America/Santo_Domingo`  
**Dominio de referencia:** `daertechglobal.com`

> Los módulos deben habilitarse según la capacidad real del VPS, la necesidad de negocio y la aprobación de seguridad. Para servicios críticos se recomienda separar bases de datos, correo, observabilidad y CI/CD.

## 1. Objetivo

Establecer un procedimiento reproducible, controlado y auditable para instalar, configurar, validar, operar, respaldar, restaurar, actualizar y migrar `infra-platform`.

## 2. Módulos

| Módulo | Servicios y propósito | Prioridad |
|---|---|---|
| `proxy` | Traefik, HTTPS, middlewares y autenticación | Obligatorio |
| `management` | Homepage, Portainer y Uptime Kuma | Alta |
| `databases` | PostgreSQL, MySQL, Redis y consolas | Según uso |
| `monitoring` | Prometheus, Grafana, Alertmanager y exporters | Obligatorio |
| `logging` | Loki, collectors, Dozzle y detección de errores | Alta |
| `messaging` | RabbitMQ, EMQX y puente de errores | Según uso |
| `storage` | MinIO | Según uso |
| `backups` | Dumps, Restic, Rclone y Dropbox | Obligatorio |
| `security` | CrowdSec y Fail2ban | Obligatorio |
| `mail` | SMTP/IMAP | Opcional especializado |
| `ci-cd` | Gitea y Registry privado | Según uso |
| `apps/daertech-platform` | Portal administrativo DAERTECH | Integración adicional |

## 3. Arquitectura lógica

```text
Usuarios y aplicaciones
          |
       Internet
          |
 DNS + Firewall + PTR/rDNS
          |
       Traefik
          |
  +-------+--------+--------------------+
  |       |        |                    |
Web UI  APIs   Consolas admin      Correo/MQTT
  |       |        |                    |
  +------- Redes Docker compartidas ----+
          |
  Datos - Mensajería - Storage
          |
  Observabilidad - Logs - Alertas
          |
  Backups locales + Restic + Dropbox
```

### Redes recomendadas

| Red | Uso |
|---|---|
| `infra_proxy` | servicios publicados por Traefik |
| `infra_monitoring` | métricas y exporters |
| `infra_logging` | ingesta y consulta de logs |
| `infra_databases` | tráfico interno de datos |
| `infra_messaging` | MQTT y AMQP |
| `infra_mail` | servicios de correo |

## 4. Dimensionamiento

| Escenario | CPU | RAM | Disco |
|---|---:|---:|---:|
| Laboratorio | 4 vCPU | 8 GB | 150 GB SSD |
| Producción inicial | 8 vCPU | 16 GB | 300 GB SSD |
| Producción completa | 12-16 vCPU | 32 GB | 500 GB+ SSD |

Ejecutar todos los servicios en un solo VPS aumenta el dominio de falla. Para cargas críticas se recomienda separar:

- bases de datos;
- correo;
- Gitea y Registry;
- observabilidad y logging;
- almacenamiento MinIO;
- brokers de mensajería.

## 5. Prerrequisitos

- Debian 12 o superior actualizado;
- IP pública fija;
- DNS administrable;
- PTR/rDNS para correo;
- Docker Engine y Compose v2;
- Git, curl, jq y apache2-utils;
- Restic y Rclone;
- almacenamiento externo;
- bot Telegram;
- política de RPO/RTO.

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y git curl jq apache2-utils rclone restic \
  ca-certificates gnupg ufw fail2ban openssl
sudo timedatectl set-timezone America/Santo_Domingo
```

## 6. DNS y puertos

### Puertos públicos

| Servicio | Puerto | Recomendación |
|---|---:|---|
| SSH | 22 | solo IP administrativa o VPN |
| HTTP | 80 | redirección a HTTPS |
| HTTPS | 443 | público |
| SMTP | 25 | solo si correo está habilitado |
| SMTPS | 465 | correo |
| Submission | 587 | correo autenticado |
| IMAPS | 993 | correo |
| MQTT | 1883 | restringido; preferir TLS |
| MQTTS | 8883 | público controlado |
| Gitea SSH | 2222 | controlado |
| AMQP | 5672 | red privada preferida |

### Subdominios de referencia

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
```

## 7. Firewall y endurecimiento

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from <IP_ADMIN>/32 to any port 22 proto tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# Abrir correo y MQTT solo si se habilitan.
sudo ufw enable
```

```bash
echo 'vm.overcommit_memory = 1' | sudo tee /etc/sysctl.d/99-infra-platform.conf
sudo sysctl --system
sudo systemctl enable --now fail2ban
```

Recomendaciones:

- SSH con llaves;
- deshabilitar login root;
- deshabilitar autenticación SSH por contraseña;
- actualizaciones de seguridad;
- NTP activo;
- rotación de logs Docker;
- mínimo privilegio;
- limitar miembros del grupo `docker`.

### Logs Docker

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "20m",
    "max-file": "5"
  }
}
```

Guardar como `/etc/docker/daemon.json` y reiniciar Docker en una ventana controlada.

## 8. Instalación de Docker

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

La membresía del grupo Docker equivale prácticamente a privilegios root.

## 9. Clonado y bootstrap

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

Para producción debe desplegarse un tag o commit aprobado y registrar el SHA.

## 10. Variables y secretos

Grupos principales:

| Grupo | Variables |
|---|---|
| General | `COMPOSE_PROJECT_NAME`, `TZ`, `DOMAIN`, `ACME_EMAIL` |
| Traefik | `TRAEFIK_DASHBOARD_HOST`, `TRAEFIK_IMAGE`, `TRAEFIK_DASHBOARD_AUTH` |
| Datos | `POSTGRES_*`, `MYSQL_*`, `REDIS_PASSWORD` |
| Monitoreo | `GRAFANA_ADMIN_*`, `PROMETHEUS_RETENTION` |
| Storage | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` |
| Mensajería | `EMQX_*`, `RABBITMQ_*` |
| Correo | `MAIL_HOSTNAME`, `MAIL_DOMAIN`, `POSTMASTER_ADDRESS` |
| CI/CD | `GITEA_DB_*`, `GITEA_SSH_PORT` |
| Alertas | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` |
| Backup | `RESTIC_*`, `BACKUP_CRON`, `RCLONE_*` |

Generar hash bcrypt para Traefik:

```bash
htpasswd -nbB admin 'CONTRASENA_LARGA_Y_UNICA'
```

Cuando el hash se use dentro de labels Compose, cada `$` debe escaparse como `$$`.

No continuar mientras `.env` conserve `CHANGE_ME` o `example.com`.

## 11. Persistencia

La plataforma utiliza bind mounts locales. Ejemplos:

```text
databases/data/postgres
databases/data/mysql
databases/data/redis
monitoring/data/grafana
monitoring/data/prometheus
logging/data/loki
messaging/data/rabbitmq
messaging/data/emqx
storage/data/minio
backups/repository
```

Reglas:

- respaldar estas rutas con Restic;
- no copiar bases activas a nivel de archivos;
- verificar UID/GID de las imágenes;
- proteger secretos con `700/600`;
- vigilar capacidad y crecimiento.

## 12. Validación previa

```bash
cd /opt/infra-platform
./scripts/validate.sh
```

Debe validar:

- todos los `docker-compose.yml`;
- sintaxis Bash;
- JSON y YAML;
- variables obligatorias;
- credenciales débiles;
- valores de ejemplo;
- bind mounts locales.

> No pasar a producción con resultados `FAIL`. Actualmente se han observado fallos preexistentes en `scripts/validate.sh` por comillas y en YAML de Loki; deben corregirse y revalidarse.

## 13. Traefik y HTTPS

```bash
docker compose --env-file .env -f proxy/docker-compose.yml config
docker compose --env-file .env -f proxy/docker-compose.yml up -d
docker compose --env-file .env -f proxy/docker-compose.yml logs -f
```

Validar:

- registros DNS;
- puertos 80/443;
- emisión ACME;
- redirección HTTPS;
- TLS moderno;
- HSTS y headers;
- autenticación de dashboards;
- respaldo de `acme.json` con permisos `600`.

## 14. Bases de datos

Servicios:

| Servicio | Uso | Consola |
|---|---|---|
| PostgreSQL | aplicaciones modernas y Gitea | pgAdmin/Adminer |
| MySQL | aplicaciones PHP y legadas | phpMyAdmin/Adminer |
| Redis | cache, sesiones y datos rápidos | Redis Commander |

```bash
docker compose --env-file .env -f databases/docker-compose.yml up -d
docker compose --env-file .env -f databases/docker-compose.yml ps
```

No publicar `5432`, `3306` o `6379` directamente a Internet.

## 15. Mensajería

### RabbitMQ

- eliminar o restringir usuario `guest`;
- crear credenciales por aplicación;
- usar vhosts separados;
- definir TTL, DLQ y límites;
- proteger consola con HTTPS.

### EMQX/MQTT

- usar TLS para acceso público;
- definir usuarios y ACL;
- separar tópicos por aplicación y ambiente;
- controlar sesiones persistentes y retención.

### Exchange de errores

```text
Exchange: app.errors
Routing keys:
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
  "timestamp": "2026-08-03T08:00:00-04:00",
  "message": "Error procesando factura",
  "correlationId": "factura-123",
  "exception": "TimeoutException",
  "endpoint": "/api/facturas",
  "statusCode": 500
}
```

## 16. MinIO

- separar API S3 y consola;
- cambiar credenciales iniciales;
- crear buckets con políticas mínimas;
- configurar versionado y lifecycle;
- respaldar datos y configuración;
- recordar que réplica no sustituye backup.

## 17. Monitoreo

| Componente | Función |
|---|---|
| Prometheus | métricas y reglas |
| Grafana | dashboards |
| Alertmanager | enrutamiento de alertas |
| Node Exporter | métricas del host |
| cAdvisor | métricas de contenedores |
| Exporters | PostgreSQL, MySQL, Redis, RabbitMQ y otros |

```bash
docker compose --env-file .env -f monitoring/docker-compose.yml up -d
curl -fsS http://127.0.0.1:9090/-/ready
```

PromQL de referencia:

```promql
sum by (application) (rate(http_requests_total[5m]))
```

```promql
sum by (application) (rate(http_requests_total{status_code=~"5.."}[5m]))
```

```promql
histogram_quantile(
  0.95,
  sum by (le, application) (rate(http_request_duration_seconds_bucket[5m]))
)
```

## 18. Logging

- Loki almacena logs;
- Alloy/Promtail recolecta logs del host y contenedores;
- Dozzle ofrece consulta rápida;
- usar logs JSON;
- incluir `application`, `environment`, `level` y `correlationId`;
- no registrar contraseñas, tokens ni datos sensibles;
- controlar tormentas de alertas por patrones de error.

```bash
docker compose --env-file .env -f logging/docker-compose.yml up -d
docker compose --env-file .env -f logging/docker-compose.yml logs --tail=200
```

## 19. Administración

| Servicio | Uso | Protección |
|---|---|---|
| Homepage | portal de accesos | HTTPS y autenticación |
| Portainer | administración Docker | VPN/IP, MFA y HTTPS |
| Uptime Kuma | disponibilidad sintética | HTTPS y autenticación |

Portainer posee privilegios críticos; debe restringirse estrictamente.

## 20. Seguridad

- CrowdSec para análisis y decisiones;
- Fail2ban para servicios del host;
- firewall deny-by-default;
- SSH por llaves;
- dashboards protegidos;
- escaneo de imágenes;
- actualización periódica;
- secretos fuera de Git;
- mínimo privilegio;
- auditoría de accesos administrativos.

## 21. CI/CD

Gitea y Registry deben configurarse con:

- HTTPS;
- autenticación fuerte;
- respaldo de base, repositorios y configuración;
- tags de imágenes inmutables;
- políticas de retención;
- runners separados cuando sea posible.

```bash
docker compose --env-file .env -f ci-cd/docker-compose.yml up -d
```

## 22. Correo

DNS obligatorio:

| Registro | Valor |
|---|---|
| A | `mail.dominio.com -> IP` |
| MX | `dominio.com -> mail.dominio.com` |
| PTR/rDNS | `IP -> mail.dominio.com` |
| SPF | autoriza IP/host |
| DKIM | clave pública generada |
| DMARC | política y reportes |

Crear cuenta:

```bash
./mail/scripts/add-account.sh usuario@dominio.com 'CONTRASENA_REAL'
```

Antes de habilitar correo:

- confirmar que el proveedor permite puerto 25;
- configurar PTR;
- probar SPF, DKIM y DMARC;
- revisar colas y reputación;
- monitorear entrega y rebotes.

## 23. Backups

```bash
./backups/scripts/backup-databases.sh
./backups/scripts/upload-dropbox.sh
sudo ./scripts/install-backup-cron.sh
cat /etc/cron.d/infra-platform-backups
```

La estrategia debe incluir:

- dumps lógicos PostgreSQL/MySQL;
- Restic para carpetas persistentes;
- Rclone para Dropbox;
- alertas Telegram;
- restauración periódica aislada;
- checksum y retención;
- segunda copia offsite si el servicio es crítico.

Un backup no se considera válido hasta restaurarlo y probar funcionalidad.

## 24. Telegram

```bash
curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  -d chat_id="${TELEGRAM_CHAT_ID}" \
  --data-urlencode text="Prueba infra-platform"
```

No enviar secretos ni datos personales. Separar chats de pruebas y producción.

## 25. Orden de despliegue

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

Desplegar por fases y comprobar health, logs, DNS y certificados antes de continuar.

## 26. Integración de aplicaciones

1. Exponer `/health` y `/metrics`.
2. Instrumentar métricas HTTP estándar.
3. Conectar a `infra_monitoring`.
4. Registrar target en `monitoring/prometheus/targets/applications.yml`.
5. Recargar Prometheus.
6. Emitir logs JSON con `correlationId`.
7. Publicar errores críticos en RabbitMQ cuando aplique.

```yaml
networks:
  monitoring:
    external: true
    name: infra_monitoring
```

```bash
curl -X POST http://localhost:9090/-/reload
```

## 27. Pruebas de aceptación

- [ ] `./scripts/validate.sh` sin fallos.
- [ ] DNS resuelve correctamente.
- [ ] Certificados HTTPS válidos.
- [ ] Routers de Traefik saludables.
- [ ] Bases accesibles solo internamente.
- [ ] Telegram probado.
- [ ] RabbitMQ y puente de errores probados.
- [ ] MQTT publish/subscribe con TLS.
- [ ] Prometheus targets UP.
- [ ] Grafana con datos.
- [ ] Logs consultables por aplicación y correlación.
- [ ] Backup generado.
- [ ] Restauración funcional.
- [ ] Correo validado si se habilita.

## 28. Operación diaria

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker stats --no-stream
df -h
./scripts/validate.sh
tail -f backups/data/database-backup.log
tail -f backups/data/dropbox-upload.log
```

Revisar:

- alertas activas;
- capacidad de disco;
- backups del día anterior;
- contenedores reiniciados;
- certificados próximos a vencer;
- colas sin consumidores;
- actualizaciones y eventos de seguridad.

## 29. Actualización y rollback

1. Confirmar backup válido.
2. Registrar commit actual.
3. Validar tag nuevo.
4. Desplegar módulo por módulo.
5. Validar health y funcionalidad.
6. Mantener versión anterior durante la ventana.
7. Revertir código o imágenes si falla.
8. Restaurar datos solo cuando sea necesario.

```bash
git rev-parse HEAD
git fetch --tags
git checkout <TAG_APROBADO>
./scripts/validate.sh
```

## 30. Migración a otro VPS

1. Congelar escrituras.
2. Ejecutar dumps y Restic.
3. Subir copia externa.
4. Preparar el nuevo VPS.
5. Clonar el mismo tag.
6. Transferir `.env`, `rclone.conf` y secretos por canal seguro.
7. Restaurar carpetas.
8. Restaurar bases.
9. Cambiar DNS.
10. Validar certificados, correo, métricas y alertas.
11. Mantener el VPS anterior disponible durante la reversión.

Nunca copiar archivos de una base activa sin detenerla o usar un snapshot consistente.

## 31. Troubleshooting

| Problema | Revisión | Acción |
|---|---|---|
| Certificado no emite | DNS, puertos y logs Traefik | corregir A/AAAA, firewall y ACME |
| Compose falla | `docker compose config` | corregir variables, rutas o YAML |
| Base unhealthy | logs, permisos y disco | corregir UID/GID o credenciales |
| Target Prometheus DOWN | red y endpoint | conectar red y registrar target |
| Sin logs en Loki | collector, mounts y labels | corregir permisos/configuración |
| Telegram no envía | token, chat y red | probar API directa |
| Dropbox falla | `rclone.conf` y OAuth | reautorizar remote |
| Correo va a spam | PTR/SPF/DKIM/DMARC | corregir DNS y reputación |
| Disco lleno | `df`, `du`, retención | rotar y ampliar capacidad |
| `validate.sh` falla | comillas y YAML Loki | corregir script y YAML |

## 32. Checklist preproducción

- [ ] VPS actualizado y endurecido.
- [ ] IP, DNS y PTR configurados.
- [ ] Todos los `CHANGE_ME` eliminados.
- [ ] Contraseñas únicas.
- [ ] Hash bcrypt real.
- [ ] Firewall y SSH validados.
- [ ] `validate.sh` sin fallos.
- [ ] Módulos seleccionados documentados.
- [ ] Backup y restore probados.
- [ ] Telegram y Dropbox probados.
- [ ] Rollback aprobado.

## 33. Checklist producción

- [ ] HTTPS válido en todas las consolas.
- [ ] MFA/VPN en consolas críticas.
- [ ] Bases y Redis no publicados.
- [ ] RabbitMQ sin `guest` remoto.
- [ ] MQTT público con TLS.
- [ ] Alertas de disponibilidad y recursos.
- [ ] Logs sin información sensible.
- [ ] Backups automáticos monitoreados.
- [ ] Correo con PTR/SPF/DKIM/DMARC.
- [ ] Tag/commit registrado.
- [ ] Evidencias y contactos de escalamiento disponibles.

## 34. Riesgos principales

| Riesgo | Tratamiento |
|---|---|
| Demasiados servicios en un VPS | separar por roles |
| Consolas públicas | VPN, allowlist, MFA y HTTPS |
| Secretos en `.env` | Vault, SOPS o Docker Secrets |
| Bind mounts con permisos incorrectos | documentar UID/GID y auditar |
| Correo propio | monitorear reputación y colas |
| Backups no restaurados | ejercicio periódico |
| Workflow general con fallos | corregir validador y Loki |
| Dependencia de Dropbox | segunda copia offsite |

## 35. Comandos rápidos

```bash
cd /opt/infra-platform
./scripts/validate.sh

docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

docker compose --env-file .env -f <modulo>/docker-compose.yml config
docker compose --env-file .env -f <modulo>/docker-compose.yml up -d
docker compose --env-file .env -f <modulo>/docker-compose.yml logs -f
```
