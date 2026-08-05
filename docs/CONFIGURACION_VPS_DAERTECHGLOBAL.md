# Configuración técnica integral del VPS para `infra-platform`

**Dominio principal:** `daertechglobal.com`  
**IP pública del VPS:** `94.72.114.98`  
**Sistema operativo objetivo:** Debian 12  
**Ruta de instalación:** `/opt/infra-platform`  
**Zona horaria:** `America/Santo_Domingo`  
**Modelo de ejecución:** Docker Engine + Docker Compose v2  
**Repositorio:** `josesrs09/infra-platform`

> Este documento es el runbook técnico de preparación, despliegue, endurecimiento, validación y operación del VPS. No debe ejecutarse en producción sin sustituir todos los secretos, probar restauración y documentar el rollback.

---

## 1. Arquitectura objetivo

```text
Internet
   |
DNS público + PTR/rDNS
   |
Firewall del proveedor + UFW
   |
94.72.114.98
   |
Traefik :80/:443
   |-----------------------------|
   |                             |
Consolas administrativas       Aplicaciones/API
   |                             |
Redes Docker externas compartidas
   |
+----------------+----------------+----------------+
|                |                |                |
Datos         Mensajería       Storage       Observabilidad
PostgreSQL    RabbitMQ         MinIO         Prometheus
MySQL         EMQX                           Grafana
Redis                                        Loki/Alloy
                                             Alertmanager
   |
Backups lógicos + Restic + Rclone/Dropbox
```

### 1.1 Módulos

| Módulo | Servicios |
|---|---|
| `proxy` | Traefik, HTTPS, middlewares, headers y autenticación administrativa |
| `management` | Homepage, Portainer y Uptime Kuma |
| `databases` | PostgreSQL, MySQL, Redis, pgAdmin, phpMyAdmin, Redis Commander y Adminer |
| `monitoring` | Prometheus, Grafana, Alertmanager, Node Exporter, cAdvisor, Blackbox y exporters |
| `logging` | Loki, Alloy, Dozzle y reglas de errores |
| `messaging` | RabbitMQ, EMQX y puente de errores a Telegram |
| `storage` | MinIO |
| `backups` | Restic backup/restore y Rclone contenedorizado |
| `security` | CrowdSec y Fail2ban; agregar bouncer antes de producción |
| `mail` | docker-mailserver y Roundcube si se habilita |
| `ci-cd` | Gitea, Registry privado y Gitea Actions Runner |

---

## 2. Dimensionamiento y límites

| Recurso | Mínimo de laboratorio | Producción inicial | Producción completa |
|---|---:|---:|---:|
| CPU | 4 vCPU | 8 vCPU | 12–16 vCPU |
| RAM | 8 GB | 16 GB | 32 GB |
| Disco SSD | 150 GB | 300 GB | 500 GB o más |
| Swap | 2 GB | 4 GB | 4–8 GB |
| IP pública | 1 | 1 fija | 1 fija + red privada/VPN |

Para ejecutar correo, Gitea Runner, bases de datos, MinIO, mensajería y observabilidad en el mismo VPS, use preferiblemente **32 GB de RAM**.

### 2.1 Capacidad de disco sugerida

```text
/                         40 GB
/var/lib/docker          100–200 GB
/opt/infra-platform      200–300 GB
Backups locales           50–100 GB
Reserva libre             mínimo 20–25 %
```

No permita que PostgreSQL, MySQL, Loki, Prometheus, Registry o MinIO consuman el 100 % del filesystem.

---

## 3. DNS público para `94.72.114.98`

Cree registros `A` con TTL inicial de `300` o `600` durante la puesta en marcha. Después puede subir a `3600`.

| Host | Tipo | Valor |
|---|---|---|
| `infra.daertechglobal.com` | A | `94.72.114.98` |
| `traefik.daertechglobal.com` | A | `94.72.114.98` |
| `portainer.daertechglobal.com` | A | `94.72.114.98` |
| `uptime.daertechglobal.com` | A | `94.72.114.98` |
| `prometheus.daertechglobal.com` | A | `94.72.114.98` |
| `grafana.daertechglobal.com` | A | `94.72.114.98` |
| `logs.daertechglobal.com` | A | `94.72.114.98` |
| `s3.daertechglobal.com` | A | `94.72.114.98` |
| `minio.daertechglobal.com` | A | `94.72.114.98` |
| `mqtt.daertechglobal.com` | A | `94.72.114.98` |
| `rabbitmq.daertechglobal.com` | A | `94.72.114.98` |
| `registry.daertechglobal.com` | A | `94.72.114.98` |
| `git.daertechglobal.com` | A | `94.72.114.98` |
| `pgadmin.daertechglobal.com` | A | `94.72.114.98` |
| `phpmyadmin.daertechglobal.com` | A | `94.72.114.98` |
| `redis.daertechglobal.com` | A | `94.72.114.98` |
| `adminer.daertechglobal.com` | A | `94.72.114.98` |
| `mail.daertechglobal.com` | A | `94.72.114.98` |
| `webmail.daertechglobal.com` | A | `94.72.114.98` |
| `api-infra.daertechglobal.com` | A | `94.72.114.98` |

### 3.1 Correo

```text
A     mail.daertechglobal.com    94.72.114.98
MX    daertechglobal.com         10 mail.daertechglobal.com
PTR   94.72.114.98               mail.daertechglobal.com
```

SPF inicial:

```text
v=spf1 mx a:mail.daertechglobal.com ip4:94.72.114.98 ~all
```

SPF definitivo después de validar emisores:

```text
v=spf1 mx a:mail.daertechglobal.com ip4:94.72.114.98 -all
```

DMARC inicial:

```text
v=DMARC1; p=none; pct=100; adkim=s; aspf=s; rua=mailto:dmarc@daertechglobal.com
```

### 3.2 Verificación DNS

```bash
dig +short infra.daertechglobal.com A
dig +short mail.daertechglobal.com A
dig +short MX daertechglobal.com
dig +short TXT daertechglobal.com
dig +short TXT _dmarc.daertechglobal.com
dig +short -x 94.72.114.98
```

El PTR debe ser configurado por el proveedor del VPS, no en Docker ni en Traefik.

---

## 4. Puertos y política de exposición

### 4.1 Públicos

| Puerto | Uso | Estado |
|---:|---|---|
| 80/tcp | ACME y redirección HTTP→HTTPS | Público |
| 443/tcp | HTTPS | Público |
| 25/tcp | SMTP servidor a servidor | Público si correo habilitado |
| 465/tcp | SMTPS | Público si correo habilitado |
| 587/tcp | Submission STARTTLS | Público si correo habilitado |
| 993/tcp | IMAPS | Público si correo habilitado |
| 8883/tcp | MQTT TLS | Público solo si es necesario |
| 2222/tcp | SSH de Gitea | Público controlado |

### 4.2 Solo redes Docker

Los siguientes puertos **no deben publicarse en el host**:

```text
1883  MQTT sin TLS
5672  RabbitMQ AMQP
5432  PostgreSQL
3306  MySQL
6379  Redis
9090  Prometheus
9093  Alertmanager
3100  Loki
15692 RabbitMQ Prometheus
18083 EMQX Management/Metrics
9000  MinIO interno, salvo publicación por Traefik
```

El Compose de mensajería debe usar `expose` para `1883`, `5672`, `15672` y `15692`; únicamente `8883` permanece publicado cuando se requiere MQTT TLS externo.

---

## 5. Preparar Debian

```bash
sudo apt update
sudo apt full-upgrade -y
sudo apt install -y \
  ca-certificates curl git gnupg jq apache2-utils openssl \
  ufw fail2ban unzip rsync vim nano htop iotop ncdu \
  dnsutils netcat-openbsd lsof chrony smartmontools

sudo timedatectl set-timezone America/Santo_Domingo
sudo systemctl enable --now chrony
sudo systemctl enable --now fail2ban
```

Verifique hora:

```bash
timedatectl
chronyc tracking
```

### 5.1 Swap

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 5.2 Sysctl

```bash
sudo tee /etc/sysctl.d/99-infra-platform.conf > /dev/null <<'EOF'
vm.overcommit_memory = 1
vm.swappiness = 10
fs.file-max = 2097152
net.core.somaxconn = 4096
net.ipv4.tcp_max_syn_backlog = 8192
net.ipv4.ip_forward = 1
net.ipv4.conf.all.rp_filter = 1
net.ipv4.conf.default.rp_filter = 1
EOF

sudo sysctl --system
```

### 5.3 Límites

```bash
sudo tee /etc/security/limits.d/99-infra-platform.conf > /dev/null <<'EOF'
* soft nofile 1048576
* hard nofile 1048576
root soft nofile 1048576
root hard nofile 1048576
EOF
```

---

## 6. Usuario administrativo y SSH

```bash
sudo adduser infraadmin
sudo usermod -aG sudo infraadmin
sudo mkdir -p /home/infraadmin/.ssh
sudo nano /home/infraadmin/.ssh/authorized_keys
sudo chown -R infraadmin:infraadmin /home/infraadmin/.ssh
sudo chmod 700 /home/infraadmin/.ssh
sudo chmod 600 /home/infraadmin/.ssh/authorized_keys
```

Cree `/etc/ssh/sshd_config.d/99-infra-platform.conf`:

```text
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
MaxAuthTries 3
LoginGraceTime 30
AllowUsers infraadmin
X11Forwarding no
AllowTcpForwarding yes
ClientAliveInterval 300
ClientAliveCountMax 2
```

Valide antes de reiniciar:

```bash
sudo sshd -t
sudo systemctl restart ssh
```

Mantenga una sesión abierta hasta confirmar que la llave funciona.

---

## 7. Firewall UFW

Sustituya `IP_ADMINISTRATIVA` por la IP desde la cual administrará el VPS.

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing

sudo ufw allow from IP_ADMINISTRATIVA/32 to any port 22 proto tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Correo
sudo ufw allow 25/tcp
sudo ufw allow 465/tcp
sudo ufw allow 587/tcp
sudo ufw allow 993/tcp

# MQTT TLS, solo si es necesario
sudo ufw allow 8883/tcp

# Gitea SSH, solo si es necesario
sudo ufw allow 2222/tcp

sudo ufw enable
sudo ufw status numbered
```

No abra `1883` ni `5672`.

> Docker puede insertar reglas iptables antes de UFW. Verifique la exposición real con `ss`, `docker ps` y un escaneo externo.

```bash
sudo ss -lntup
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

---

## 8. Instalar Docker Engine

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker infraadmin
```

### 8.1 Docker daemon

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "20m",
    "max-file": "5"
  },
  "live-restore": true,
  "userland-proxy": false,
  "default-address-pools": [
    {"base": "172.30.0.0/16", "size": 24}
  ]
}
EOF

sudo systemctl restart docker
```

Verifique:

```bash
docker info
docker compose version
```

El grupo `docker` equivale a privilegios root. Limite sus miembros.

---

## 9. Preparar almacenamiento y repositorio

```bash
sudo mkdir -p /opt/infra-platform
sudo chown infraadmin:infraadmin /opt/infra-platform
sudo -u infraadmin git clone https://github.com/josesrs09/infra-platform.git /opt/infra-platform
cd /opt/infra-platform
git checkout feature/daertech-platform-foundation
cp .env.example .env
chmod 600 .env
```

Para producción, use un tag o SHA aprobado:

```bash
git rev-parse HEAD
```

Ejecute bootstrap:

```bash
sudo ./scripts/bootstrap.sh
```

Verifique rutas persistentes:

```text
databases/data/postgres
databases/data/mysql
databases/data/redis
monitoring/data/prometheus
monitoring/data/grafana
monitoring/data/alertmanager
logging/data/loki
logging/data/alloy
messaging/data/rabbitmq
messaging/data/emqx
storage/data/minio
ci-cd/data/gitea
ci-cd/data/registry
ci-cd/data/act-runner
mail/data/mail
mail/data/state
mail/config
backups/data
backups/repository
backups/rclone
```

---

## 10. Redes Docker externas

El bootstrap debe crear como mínimo:

```bash
docker network create infra_proxy || true
docker network create infra_backend || true
docker network create infra_database || true
docker network create infra_monitoring || true
docker network create infra_logging || true
docker network create infra_messaging || true
docker network create infra_storage || true
docker network create infra_security || true
```

Verifique:

```bash
docker network ls | grep infra_
```

No conecte un contenedor a más redes de las necesarias.

---

## 11. Configurar `.env`

```bash
nano /opt/infra-platform/.env
```

### 11.1 Base

```dotenv
COMPOSE_PROJECT_NAME=infra-platform
TZ=America/Santo_Domingo
VPS_PUBLIC_IP=94.72.114.98
DOMAIN=daertechglobal.com
ACME_EMAIL=infraestructura@daertechglobal.com
```

### 11.2 Hosts

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
WEBMAIL_HOST=webmail.daertechglobal.com
```

### 11.3 Secretos

Genere secretos distintos:

```bash
openssl rand -base64 48
openssl rand -hex 32
```

No reutilice contraseñas entre servicios.

### 11.4 Hash administrativo Traefik

No guarde un hash de ejemplo en el archivo dinámico.

```bash
htpasswd -nbB admin 'CONTRASENA_ADMINISTRATIVA_LARGA'
```

Escape cada `$` como `$$` si el valor se interpreta por Compose.

```dotenv
TRAEFIK_DASHBOARD_AUTH=admin:HASH_BCRYPT_ESCAPADO
```

El procedimiento de renderización debe generar el middleware antes de iniciar Traefik. Verifique que `proxy/dynamic/middlewares.yml` no contenga `CHANGE_THIS_BCRYPT_HASH`.

```bash
grep -R "CHANGE_THIS_BCRYPT_HASH\|REPLACE_WITH_ESCAPED" proxy .env
```

### 11.5 Gitea Actions Runner

```dotenv
GITEA_RUNNER_REGISTRATION_TOKEN=TOKEN_GENERADO_EN_GITEA
GITEA_RUNNER_NAME=infra-runner-01
GITEA_RUNNER_LABELS=ubuntu-latest:docker://node:22-bookworm
```

El token se genera después de iniciar Gitea:

```text
Site Administration → Actions → Runners → Create new Runner
```

El runner monta `/var/run/docker.sock`; trátelo como componente con privilegios root.

### 11.6 Backups

```dotenv
RESTIC_REPOSITORY=/repository
RESTIC_PASSWORD=SECRETO_UNICO
BACKUP_CRON=0 2 * * *
RCLONE_DROPBOX_REMOTE=dropbox
RCLONE_DROPBOX_PATH=infra-platform/backups
```

Rclone ya se ejecuta en el contenedor `infra-rclone-upload`. El host solo necesita crear y proteger `rclone.conf`.

---

## 12. Configurar Rclone/Dropbox

Puede generar la configuración con un contenedor temporal:

```bash
cd /opt/infra-platform
mkdir -p backups/rclone
chmod 700 backups/rclone

docker run --rm -it \
  -v "$PWD/backups/rclone:/config/rclone" \
  rclone/rclone:latest config
```

Cree el remote `dropbox`.

Proteja:

```bash
chmod 600 backups/rclone/rclone.conf
```

Pruebe sin instalar Rclone en el host:

```bash
docker run --rm \
  -v "$PWD/backups/rclone/rclone.conf:/config/rclone/rclone.conf:ro" \
  rclone/rclone:latest \
  lsd dropbox: --config /config/rclone/rclone.conf
```

Ejecute la carga:

```bash
docker compose --env-file .env -f backups/docker-compose.yml \
  --profile upload run --rm rclone-upload
```

---

## 13. Traefik, HTTPS y middlewares

Antes de iniciar:

```bash
mkdir -p proxy/letsencrypt proxy/logs
chmod 700 proxy/letsencrypt
touch proxy/letsencrypt/acme.json
chmod 600 proxy/letsencrypt/acme.json
```

Confirme que `proxy/traefik.yml` usa:

```yaml
certificatesResolvers:
  letsencrypt:
    acme:
      email: infraestructura@daertechglobal.com
      storage: /letsencrypt/acme.json
```

Métricas Traefik:

```yaml
metrics:
  prometheus:
    entryPoint: metrics
```

Prometheus debe consultar:

```text
infra-traefik:8080
```

Valide:

```bash
docker compose --env-file .env -f proxy/docker-compose.yml config
docker compose --env-file .env -f proxy/docker-compose.yml up -d
docker logs -f infra-traefik
```

---

## 14. Bases de datos

```bash
docker compose --env-file .env -f databases/docker-compose.yml config
docker compose --env-file .env -f databases/docker-compose.yml up -d
```

Verifique:

```bash
docker exec infra-postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"
docker exec infra-mysql mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD"
docker exec infra-redis redis-cli -a "$REDIS_PASSWORD" PING
```

No publique `5432`, `3306` ni `6379`.

---

## 15. Mensajería

### 15.1 RabbitMQ

El plugin `rabbitmq_prometheus` debe estar habilitado. Las métricas se exponen internamente en:

```text
infra-rabbitmq:15692
```

AMQP `5672` queda solo en `infra_messaging`.

### 15.2 EMQX

`1883` queda interno. `8883` se publica únicamente para MQTT TLS.

Prometheus consulta las métricas nativas en:

```text
http://infra-emqx:18083/api/v5/prometheus/stats
```

Verifique la ruta exacta en la versión de EMQX desplegada; si requiere autenticación, configure credenciales de scrape o integración Prometheus desde el dashboard.

### 15.3 Arranque

```bash
docker compose --env-file .env -f messaging/docker-compose.yml config
docker compose --env-file .env -f messaging/docker-compose.yml up -d
```

Confirme que el host no escucha 1883/5672:

```bash
sudo ss -lntp | grep -E ':1883|:5672' && echo 'ERROR: puerto publicado' || true
```

---

## 16. MinIO y métricas

MinIO debe conectarse a `infra_monitoring` y utilizar:

```dotenv
MINIO_PROMETHEUS_AUTH_TYPE=public
```

La ruta interna de métricas es:

```text
http://infra-minio:9000/minio/v2/metrics/cluster
```

No publique las métricas directamente en Internet.

---

## 17. Monitoreo

Prometheus debe incluir targets:

```text
infra-traefik:8080
infra-rabbitmq:15692
infra-emqx:18083/api/v5/prometheus/stats
infra-minio:9000/minio/v2/metrics/cluster
```

Arranque:

```bash
docker compose --env-file .env -f monitoring/docker-compose.yml config
docker compose --env-file .env -f monitoring/docker-compose.yml up -d
```

Verifique desde Prometheus:

```text
Status → Targets
```

Todos los targets habilitados deben aparecer `UP`.

Pruebas internas:

```bash
docker exec infra-prometheus wget -qO- http://infra-traefik:8080/metrics | head
docker exec infra-prometheus wget -qO- http://infra-rabbitmq:15692/metrics | head
docker exec infra-prometheus wget -qO- http://infra-minio:9000/minio/v2/metrics/cluster | head
```

---

## 18. Logging y alertas Loki → Alertmanager → Telegram

Flujo requerido:

```text
Logs Docker
   ↓
Alloy
   ↓
Loki
   ↓
Loki Ruler
   ↓
Alertmanager
   ↓
Telegram
```

Loki debe incluir:

```yaml
ruler:
  storage:
    type: local
  alertmanager_url: http://infra-alertmanager:9093
  enable_alertmanager_v2: true
```

Las reglas se montan en:

```text
logging/loki/rules/fake/application-errors.yml
```

Incluyen detección de:

- `critical`;
- `fatal`;
- `panic`;
- `unhandled exception`;
- ráfagas de `error`, `exception` o HTTP 5xx.

Arranque:

```bash
docker compose --env-file .env -f logging/docker-compose.yml config
docker compose --env-file .env -f logging/docker-compose.yml up -d
```

Prueba controlada:

```bash
docker run --rm --name log-alert-test alpine \
  sh -c 'echo "CRITICAL test infra-platform unhandled exception"; sleep 90'
```

Verifique:

```bash
docker logs infra-loki --since=10m
docker logs infra-alertmanager --since=10m
```

Debe recibirse una alerta Telegram. Si no ocurre, revise labels generados por Alloy, grupo tenant `fake`, rutas de reglas y token/chat de Telegram.

---

## 19. CI/CD y Gitea Runner

Primero inicie Gitea y Registry:

```bash
docker compose --env-file .env -f ci-cd/docker-compose.yml up -d gitea registry
```

Complete el asistente de Gitea y genere el token del runner. Actualice `.env` y luego:

```bash
docker compose --env-file .env -f ci-cd/docker-compose.yml up -d gitea-runner
```

Verifique:

```bash
docker logs -f infra-gitea-runner
```

En Gitea, el runner debe aparecer `Idle` o `Online`.

> El runner controla Docker mediante el socket. Ejecute únicamente workflows confiables y repositorios autorizados.

---

## 20. Correo SMTP/IMAP

Variables:

```dotenv
MAIL_HOSTNAME=mail
MAIL_DOMAIN=daertechglobal.com
POSTMASTER_ADDRESS=postmaster@daertechglobal.com
```

Puertos:

```text
25, 465, 587, 993
```

El certificado debe existir en:

```text
/etc/letsencrypt/live/mail.daertechglobal.com/
```

Configure DNS A, MX, PTR, SPF, DKIM y DMARC antes de enviar volumen real.

Arranque:

```bash
docker compose --env-file .env -f mail/docker-compose.yml config
docker compose --env-file .env -f mail/docker-compose.yml up -d
```

---

## 21. Seguridad

Arranque CrowdSec y Fail2ban:

```bash
docker compose --env-file .env -f security/docker-compose.yml up -d
```

Pendiente obligatorio para bloqueo real de CrowdSec:

```text
CrowdSec Firewall Bouncer o Traefik CrowdSec Bouncer
```

Hasta agregar el bouncer, CrowdSec puede generar decisiones sin bloquear tráfico.

Proteja todas las consolas administrativas con:

- HTTPS;
- hash bcrypt real;
- contraseña propia del servicio;
- MFA donde esté disponible;
- VPN o allowlist de IP para Portainer, Traefik, Grafana y Gitea.

---

## 22. Orden de despliegue

```bash
cd /opt/infra-platform

./scripts/validate.sh

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

Después de cada módulo:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker compose --env-file .env -f MODULO/docker-compose.yml logs --tail=200
```

---

## 23. Backups y restauración

### 23.1 Restic

```bash
docker compose --env-file .env -f backups/docker-compose.yml run --rm restic-backup
```

### 23.2 Rclone contenedorizado

```bash
docker compose --env-file .env -f backups/docker-compose.yml \
  --profile upload run --rm rclone-upload
```

### 23.3 Copias lógicas

Ejecute los scripts de dumps antes de Restic/Rclone:

```bash
./backups/scripts/backup-databases.sh
```

### 23.4 Validación

```bash
ls -lah backups/data
ls -lah backups/repository
```

Pruebe restauración en un entorno aislado. Un backup sin prueba de restore no se considera válido.

---

## 24. Validación integral

```bash
cd /opt/infra-platform
./scripts/validate.sh
```

Controles mínimos:

```bash
docker compose --env-file .env -f proxy/docker-compose.yml config --quiet
docker compose --env-file .env -f databases/docker-compose.yml config --quiet
docker compose --env-file .env -f monitoring/docker-compose.yml config --quiet
docker compose --env-file .env -f logging/docker-compose.yml config --quiet
docker compose --env-file .env -f messaging/docker-compose.yml config --quiet
docker compose --env-file .env -f storage/docker-compose.yml config --quiet
docker compose --env-file .env -f backups/docker-compose.yml config --quiet
docker compose --env-file .env -f security/docker-compose.yml config --quiet
docker compose --env-file .env -f mail/docker-compose.yml config --quiet
docker compose --env-file .env -f ci-cd/docker-compose.yml config --quiet
```

Verifique placeholders:

```bash
grep -RInE 'CHANGE_ME|example\.com|CHANGE_THIS_BCRYPT_HASH|REPLACE_WITH_ESCAPED' \
  .env proxy management databases monitoring logging messaging storage security mail ci-cd
```

---

## 25. Pruebas posteriores

### HTTPS

```bash
curl -I https://infra.daertechglobal.com
curl -I https://grafana.daertechglobal.com
curl -I https://git.daertechglobal.com
```

### Puertos

```bash
sudo ss -lntup
```

No deben aparecer `1883` ni `5672` escuchando en `0.0.0.0`.

### Métricas

En Prometheus, consulte:

```promql
up{job="traefik"}
up{job="rabbitmq"}
up{job="emqx"}
up{job="minio"}
```

### Recursos

```bash
docker stats --no-stream
df -h
du -sh /var/lib/docker /opt/infra-platform/* | sort -h
```

---

## 26. Operación diaria

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker stats --no-stream
df -h
docker system df
```

Revise:

- contenedores reiniciados;
- targets Prometheus DOWN;
- alertas activas;
- cola RabbitMQ;
- sesiones y conexiones de bases;
- crecimiento de Loki/Prometheus;
- espacio de MinIO/Registry;
- cola SMTP;
- resultado de backups;
- certificados próximos a vencer.

No ejecute `docker system prune -a` sin revisar impacto.

---

## 27. Actualización y rollback

Antes de actualizar:

```bash
git rev-parse HEAD
./backups/scripts/backup-databases.sh
docker compose --env-file .env -f backups/docker-compose.yml run --rm restic-backup
```

Actualización:

```bash
git fetch --tags
git checkout TAG_APROBADO
./scripts/validate.sh
docker compose --env-file .env -f MODULO/docker-compose.yml pull
docker compose --env-file .env -f MODULO/docker-compose.yml up -d
```

Rollback:

1. volver al SHA/tag anterior;
2. recrear contenedores;
3. restaurar configuración;
4. restaurar datos solo cuando sea necesario;
5. validar health, logs y funcionalidad.

---

## 28. Checklist de producción

- [ ] Todos los registros A apuntan a `94.72.114.98`.
- [ ] PTR apunta a `mail.daertechglobal.com`.
- [ ] UFW activo y SSH limitado.
- [ ] Puertos `1883` y `5672` no publicados.
- [ ] Docker con rotación de logs.
- [ ] Al menos 20 % de disco libre.
- [ ] `.env` sin valores de ejemplo.
- [ ] Hash bcrypt administrativo real.
- [ ] Traefik emite certificados correctamente.
- [ ] PostgreSQL, MySQL y Redis no están expuestos.
- [ ] RabbitMQ, EMQX, MinIO y Traefik aparecen `UP` en Prometheus.
- [ ] Loki envía alertas a Alertmanager.
- [ ] Alertmanager envía a Telegram.
- [ ] Gitea Runner aparece online.
- [ ] Rclone funciona dentro del contenedor.
- [ ] Backup lógico, Restic y Dropbox probados.
- [ ] Restauración validada en ambiente aislado.
- [ ] SMTP con SPF, DKIM y DMARC PASS.
- [ ] CrowdSec bouncer planificado o implementado.
- [ ] Commit/tag desplegado registrado.
- [ ] Plan de rollback aprobado.
