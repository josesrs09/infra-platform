# Configuración detallada del VPS para `infra-platform`

**Dominio principal:** `daertechglobal.com`  
**Sistema operativo recomendado:** Debian 12  
**Ruta de instalación:** `/opt/infra-platform`  
**Zona horaria:** `America/Santo_Domingo`  
**Modelo:** Docker Engine + Docker Compose v2

> Este documento describe lo que debe configurarse manualmente en el VPS antes de desplegar `infra-platform`.

---

## 1. Requisitos mínimos del VPS

| Recurso | Mínimo | Recomendado |
|---|---:|---:|
| CPU | 8 vCPU | 12–16 vCPU |
| RAM | 16 GB | 32 GB |
| Disco | 300 GB SSD | 500 GB SSD o más |
| IP | Pública fija | Pública fija + red privada/VPN |
| Sistema operativo | Debian 12 | Debian 12 actualizado |

Para ejecutar todos los módulos en un único VPS, incluyendo correo, bases de datos, mensajería, observabilidad y CI/CD, se recomienda **32 GB de RAM**.

---

## 2. DNS que debe configurar

Cree registros `A` apuntando a la IP pública del VPS.

| Host | Servicio |
|---|---|
| `infra.daertechglobal.com` | Homepage / portal principal |
| `traefik.daertechglobal.com` | Dashboard Traefik |
| `portainer.daertechglobal.com` | Portainer |
| `uptime.daertechglobal.com` | Uptime Kuma |
| `prometheus.daertechglobal.com` | Prometheus |
| `grafana.daertechglobal.com` | Grafana |
| `logs.daertechglobal.com` | Dozzle / consola de logs |
| `s3.daertechglobal.com` | API S3 de MinIO |
| `minio.daertechglobal.com` | Consola MinIO |
| `mqtt.daertechglobal.com` | EMQX / MQTT |
| `rabbitmq.daertechglobal.com` | RabbitMQ Management |
| `registry.daertechglobal.com` | Registry privado |
| `git.daertechglobal.com` | Gitea |
| `pgadmin.daertechglobal.com` | pgAdmin |
| `phpmyadmin.daertechglobal.com` | phpMyAdmin |
| `redis.daertechglobal.com` | Redis Commander |
| `adminer.daertechglobal.com` | Adminer |
| `mail.daertechglobal.com` | Servidor de correo |
| `api-infra.daertechglobal.com` | API de DAERTECH Platform |

### Registros adicionales para correo

```text
A     mail.daertechglobal.com      -> IP_PUBLICA_VPS
MX    daertechglobal.com           -> mail.daertechglobal.com (prioridad 10)
PTR   IP_PUBLICA_VPS               -> mail.daertechglobal.com
```

### SPF recomendado

```text
v=spf1 mx a:mail.daertechglobal.com ip4:IP_PUBLICA_VPS -all
```

### DMARC inicial recomendado

```text
_dmarc.daertechglobal.com TXT "v=DMARC1; p=none; rua=mailto:dmarc@daertechglobal.com; adkim=s; aspf=s"
```

Después de validar DKIM y entregabilidad, cambie `p=none` por `quarantine` o `reject`.

---

## 3. Puertos que debe permitir

### Públicos obligatorios

```text
80/tcp    HTTP para redirección y validación ACME
443/tcp   HTTPS
```

### Administrativos

```text
22/tcp    SSH, solamente desde IP administrativa o VPN
2222/tcp  SSH de Gitea, si se utilizará
```

### Correo

```text
25/tcp    SMTP servidor a servidor
465/tcp   SMTPS
587/tcp   Submission
993/tcp   IMAPS
```

### Mensajería

```text
1883/tcp  MQTT sin TLS, preferiblemente solo red privada
8883/tcp  MQTT con TLS
5672/tcp  AMQP, preferiblemente solo red privada
```

### No publicar a Internet

```text
5432 PostgreSQL
3306 MySQL
6379 Redis
9090 Prometheus
9093 Alertmanager
3100 Loki
```

---

## 4. Actualizar y preparar Debian

```bash
sudo apt update
sudo apt full-upgrade -y
sudo apt install -y \
  ca-certificates \
  curl \
  git \
  gnupg \
  jq \
  apache2-utils \
  openssl \
  ufw \
  fail2ban \
  rclone \
  restic \
  unzip \
  rsync

sudo timedatectl set-timezone America/Santo_Domingo
sudo systemctl enable --now fail2ban
```

### Parámetro recomendado para Redis

```bash
echo 'vm.overcommit_memory = 1' | sudo tee /etc/sysctl.d/99-infra-platform.conf
sudo sysctl --system
```

---

## 5. Configurar SSH

Cree un usuario administrativo:

```bash
sudo adduser infraadmin
sudo usermod -aG sudo infraadmin
```

Copie la llave pública:

```bash
sudo mkdir -p /home/infraadmin/.ssh
sudo nano /home/infraadmin/.ssh/authorized_keys
sudo chown -R infraadmin:infraadmin /home/infraadmin/.ssh
sudo chmod 700 /home/infraadmin/.ssh
sudo chmod 600 /home/infraadmin/.ssh/authorized_keys
```

Configure `/etc/ssh/sshd_config`:

```text
PermitRootLogin no
PasswordAuthentication no
PubkeyAuthentication yes
```

Reinicie SSH:

```bash
sudo systemctl restart ssh
```

> No deshabilite la contraseña hasta confirmar que la autenticación por llave funciona.

---

## 6. Configurar firewall UFW

Reemplace `IP_ADMINISTRATIVA` por su IP pública autorizada.

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing

sudo ufw allow from IP_ADMINISTRATIVA/32 to any port 22 proto tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Solo si se habilita correo
sudo ufw allow 25/tcp
sudo ufw allow 465/tcp
sudo ufw allow 587/tcp
sudo ufw allow 993/tcp

# Solo si se habilita MQTT público
sudo ufw allow 8883/tcp

# Solo si se habilita Git SSH
sudo ufw allow 2222/tcp

sudo ufw enable
sudo ufw status verbose
```

---

## 7. Instalar Docker Engine y Compose

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin

sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Cierre sesión y vuelva a entrar para aplicar el grupo `docker`.

### Rotación de logs Docker

Cree `/etc/docker/daemon.json`:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "20m",
    "max-file": "5"
  }
}
```

Reinicie Docker:

```bash
sudo systemctl restart docker
```

---

## 8. Clonar el repositorio

```bash
sudo mkdir -p /opt/infra-platform
sudo chown "$USER":"$USER" /opt/infra-platform

git clone https://github.com/josesrs09/infra-platform.git /opt/infra-platform
cd /opt/infra-platform

git checkout feature/daertech-platform-foundation
cp .env.example .env
chmod 600 .env
```

Para producción utilice un tag o commit aprobado:

```bash
git rev-parse HEAD
```

Registre ese SHA en la solicitud de cambio.

---

## 9. Crear directorios persistentes

```bash
cd /opt/infra-platform
sudo ./scripts/bootstrap.sh
```

Verifique que existan directorios como:

```text
databases/data/postgres
databases/data/mysql
databases/data/redis
monitoring/data/prometheus
monitoring/data/grafana
logging/data/loki
messaging/data/rabbitmq
messaging/data/emqx
storage/data/minio
backups/repository
backups/rclone
```

Proteja secretos:

```bash
chmod 700 backups/rclone
chmod 600 backups/rclone/rclone.conf 2>/dev/null || true
```

---

## 10. Configurar el archivo `.env`

Edite:

```bash
nano /opt/infra-platform/.env
```

### Dominio y ACME

```dotenv
COMPOSE_PROJECT_NAME=infra-platform
TZ=America/Santo_Domingo
DOMAIN=daertechglobal.com
ACME_EMAIL=infraestructura@daertechglobal.com
```

### Hosts

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

### Generar hash bcrypt para Traefik

```bash
htpasswd -nbB admin 'CONTRASENA_LARGA_Y_UNICA'
```

Guarde el resultado en:

```dotenv
TRAEFIK_DASHBOARD_AUTH=admin:HASH_BCRYPT_GENERADO
```

Cuando el hash se use dentro de etiquetas Compose, escape cada `$` como `$$`.

### Generar secretos

```bash
openssl rand -base64 48
openssl rand -hex 32
openssl rand -base64 64
```

Use contraseñas distintas para cada servicio:

```dotenv
GRAFANA_ADMIN_PASSWORD=<SECRETO_UNICO>
POSTGRES_PASSWORD=<SECRETO_UNICO>
MYSQL_PASSWORD=<SECRETO_UNICO>
MYSQL_ROOT_PASSWORD=<SECRETO_UNICO>
REDIS_PASSWORD=<SECRETO_UNICO>
PGADMIN_PASSWORD=<SECRETO_UNICO>
REDIS_COMMANDER_PASSWORD=<SECRETO_UNICO>
MINIO_ROOT_PASSWORD=<SECRETO_UNICO>
EMQX_DASHBOARD_PASSWORD=<SECRETO_UNICO>
RABBITMQ_PASSWORD=<SECRETO_UNICO>
GITEA_DB_PASSWORD=<SECRETO_UNICO>
RESTIC_PASSWORD=<SECRETO_UNICO>
```

### Correo

```dotenv
MAIL_HOSTNAME=mail
MAIL_DOMAIN=daertechglobal.com
POSTMASTER_ADDRESS=postmaster@daertechglobal.com
```

### Telegram

```dotenv
TELEGRAM_BOT_TOKEN=<TOKEN_REAL>
TELEGRAM_CHAT_ID=<CHAT_ID_REAL>
```

### Backups

```dotenv
RESTIC_REPOSITORY=/repository
BACKUP_CRON=0 2 * * *
RCLONE_DROPBOX_REMOTE=dropbox
RCLONE_DROPBOX_PATH=infra-platform/backups
```

---

## 11. Configurar Rclone con Dropbox

```bash
rclone config
```

Cree un remote llamado:

```text
dropbox
```

Guarde el archivo en:

```text
/opt/infra-platform/backups/rclone/rclone.conf
```

Pruebe:

```bash
rclone --config /opt/infra-platform/backups/rclone/rclone.conf lsd dropbox:
```

---

## 12. Validar antes de desplegar

```bash
cd /opt/infra-platform
./scripts/validate.sh
```

Debe validar:

- archivos Compose;
- sintaxis Bash;
- YAML y JSON;
- variables obligatorias;
- ausencia de `CHANGE_ME`;
- ausencia de dominios de ejemplo;
- persistencia local.

> No continúe a producción mientras exista un resultado `FAIL`.

---

## 13. Orden de despliegue

Ejecute módulo por módulo:

```bash
cd /opt/infra-platform

# 1. Proxy y certificados
docker compose --env-file .env -f proxy/docker-compose.yml up -d

# 2. Bases de datos
docker compose --env-file .env -f databases/docker-compose.yml up -d

# 3. Mensajería
docker compose --env-file .env -f messaging/docker-compose.yml up -d

# 4. Almacenamiento
docker compose --env-file .env -f storage/docker-compose.yml up -d

# 5. Monitoreo
docker compose --env-file .env -f monitoring/docker-compose.yml up -d

# 6. Logging
docker compose --env-file .env -f logging/docker-compose.yml up -d

# 7. Seguridad
docker compose --env-file .env -f security/docker-compose.yml up -d

# 8. Administración
docker compose --env-file .env -f management/docker-compose.yml up -d

# 9. CI/CD
docker compose --env-file .env -f ci-cd/docker-compose.yml up -d

# 10. Correo
docker compose --env-file .env -f mail/docker-compose.yml up -d
```

Después de cada módulo:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker compose --env-file .env -f MODULO/docker-compose.yml logs --tail=200
```

---

## 14. Implementar DAERTECH Platform

```bash
cd /opt/infra-platform/apps/daertech-platform
cp .env.example .env
chmod 600 .env
```

Confirme:

```dotenv
PUBLIC_FRONTEND_URL=https://infra.daertechglobal.com
PUBLIC_API_URL=https://api-infra.daertechglobal.com
ADMIN_EMAIL=admin@daertechglobal.com
```

Mantenga inicialmente:

```dotenv
DEPLOYMENT_EXECUTION_ENABLED=false
```

Arranque:

```bash
docker compose config --quiet
docker compose build --no-cache
docker compose up -d
```

Validación:

```bash
curl -fsS http://127.0.0.1:8080/api/v1/actuator/health
```

---

## 15. Validaciones posteriores

### HTTPS

```bash
curl -I https://infra.daertechglobal.com
curl -I https://grafana.daertechglobal.com
curl -I https://api-infra.daertechglobal.com/api/v1/actuator/health
```

### Contenedores

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker stats --no-stream
```

### Disco

```bash
df -h
du -sh /opt/infra-platform/* | sort -h
```

### Backup manual

```bash
cd /opt/infra-platform
./backups/scripts/backup-databases.sh
./backups/scripts/upload-dropbox.sh
```

### Logs de backup

```bash
tail -f backups/data/database-backup.log
tail -f backups/data/dropbox-upload.log
```

---

## 16. Checklist final del VPS

- [ ] Debian actualizado.
- [ ] Zona horaria configurada.
- [ ] SSH por llave funcionando.
- [ ] Login root deshabilitado.
- [ ] Firewall activo.
- [ ] Fail2ban activo.
- [ ] Docker y Compose instalados.
- [ ] Rotación de logs Docker configurada.
- [ ] DNS de todos los subdominios apuntando al VPS.
- [ ] PTR/rDNS configurado para correo.
- [ ] `.env` sin valores `CHANGE_ME`.
- [ ] Contraseñas únicas por servicio.
- [ ] Traefik con HTTPS válido.
- [ ] PostgreSQL, MySQL y Redis no expuestos públicamente.
- [ ] Grafana, Portainer y Traefik protegidos.
- [ ] Telegram probado.
- [ ] Rclone/Dropbox probado.
- [ ] Backup manual exitoso.
- [ ] Restauración probada en entorno aislado.
- [ ] Prometheus con targets `UP`.
- [ ] Loki recibiendo logs.
- [ ] Uptime Kuma monitoreando servicios.
- [ ] Correo con SPF, DKIM y DMARC.
- [ ] Commit o tag desplegado documentado.

---

## 17. Datos que debe conservar fuera de Git

Nunca cargue al repositorio:

```text
.env
backups/rclone/rclone.conf
acme.json
llaves SSH privadas
certificados privados
contraseñas
API tokens
TELEGRAM_BOT_TOKEN
RESTIC_PASSWORD
```

Use un gestor de secretos o un almacenamiento cifrado y restringido.
