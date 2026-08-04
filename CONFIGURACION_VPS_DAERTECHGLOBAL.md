# CONFIGURACIÓN VPS DAERTECHGLOBAL

Implementación general de la infraestructura Docker de producción para `daertechglobal.com`, incluyendo Traefik, `docker-mailserver:14.0.0`, Roundcube, MariaDB, monitoreo, logs, seguridad y respaldos.

## 1. Datos base

| Elemento | Valor |
|---|---|
| Sistema operativo | Debian 12 o superior |
| Dominio | `daertechglobal.com` |
| Correo | `mail.daertechglobal.com` |
| Webmail | `webmail.daertechglobal.com` |
| Ruta | `/opt/infra-platform` |
| Repositorio | `josesrs09/infra-platform` |
| Zona horaria | `America/Santo_Domingo` |
| Proxy HTTPS | Traefik |
| Servidor SMTP/IMAP | `docker-mailserver:14.0.0` |
| Cliente web | Roundcube |
| Base de datos Roundcube | MariaDB |

## 2. Arquitectura

```text
Internet
   |
DNS + Firewall + PTR/rDNS
   |
   +-- 80/443 ---------------- Traefik
   |                              |
   |                              +-- webmail.daertechglobal.com -> Roundcube
   |                              +-- grafana.daertechglobal.com
   |                              +-- infra.daertechglobal.com
   |                              +-- demás aplicaciones
   |
   +-- 25/465/587/993/4190 ---- docker-mailserver
                                      |
                                      +-- Postfix
                                      +-- Dovecot
                                      +-- Rspamd
                                      +-- DKIM/DMARC
                                      +-- Fail2ban
                                      |
                                   Roundcube
                                      |
                                   MariaDB

Prometheus + Grafana + Loki + Alertmanager
Restic + Rclone + almacenamiento externo
```

Traefik publica únicamente HTTP/HTTPS. SMTP, Submission, IMAPS y ManageSieve se publican directamente desde `docker-mailserver`.

## 3. Recursos recomendados

Para ejecutar correo, observabilidad, bases de datos, mensajería y aplicaciones en un solo VPS:

- 8 vCPU mínimo; 12 a 16 vCPU recomendado.
- 16 GB RAM mínimo; 32 GB recomendado.
- 300 GB SSD mínimo.
- IP pública fija.
- Puerto 25 habilitado de entrada y salida.
- PTR/rDNS administrable.
- Al menos 25 % de espacio libre.

## 4. Preparar Debian

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y \
  ca-certificates curl git gnupg jq openssl \
  ufw fail2ban certbot dnsutils netcat-openbsd \
  restic rclone

sudo timedatectl set-timezone America/Santo_Domingo
sudo systemctl enable --now fail2ban
```

Configurar parámetros del host:

```bash
echo 'vm.overcommit_memory = 1' | sudo tee /etc/sysctl.d/99-infra-platform.conf
sudo sysctl --system
```

Configurar rotación de logs Docker en `/etc/docker/daemon.json`:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "20m",
    "max-file": "5"
  }
}
```

```bash
sudo systemctl restart docker
```

## 5. Instalar Docker

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

printf '%s\n' \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Cierre y vuelva a abrir la sesión antes de usar Docker sin `sudo`.

## 6. Clonar y preparar el proyecto

```bash
sudo mkdir -p /opt/infra-platform
sudo chown "$USER":"$USER" /opt/infra-platform

git clone https://github.com/josesrs09/infra-platform.git /opt/infra-platform
cd /opt/infra-platform

git fetch --all --prune
git checkout agent/integrate-roundcube

cp .env.example .env
chmod 600 .env
sudo ./scripts/bootstrap.sh
```

En producción debe desplegarse un tag o commit aprobado:

```bash
git rev-parse HEAD
```

## 7. Variables de entorno

Edite `/opt/infra-platform/.env`:

```dotenv
COMPOSE_PROJECT_NAME=infra-platform
TZ=America/Santo_Domingo
DOMAIN=daertechglobal.com
ACME_EMAIL=admin@daertechglobal.com

MAIL_HOSTNAME=mail
MAIL_DOMAIN=daertechglobal.com
POSTMASTER_ADDRESS=postmaster@daertechglobal.com
WEBMAIL_HOST=webmail.daertechglobal.com

ROUNDCUBE_DB_NAME=roundcubemail
ROUNDCUBE_DB_USER=roundcube
ROUNDCUBE_DB_PASSWORD=GENERAR_CLAVE_SEGURA
ROUNDCUBE_DB_ROOT_PASSWORD=GENERAR_CLAVE_ROOT_SEGURA
```

Genere contraseñas:

```bash
openssl rand -base64 36
openssl rand -base64 36
```

No suba `.env`, certificados, claves privadas, tokens ni respaldos al repositorio.

## 8. Redes Docker

Compruebe las redes compartidas:

```bash
docker network ls | grep infra_
```

El módulo de correo usa:

- `infra_proxy`: Traefik y Roundcube.
- `infra_backend`: docker-mailserver, Roundcube y MariaDB.
- `infra_monitoring`: métricas y supervisión.

Créelas si no existen:

```bash
docker network create infra_proxy 2>/dev/null || true
docker network create infra_backend 2>/dev/null || true
docker network create infra_monitoring 2>/dev/null || true
```

## 9. DNS del correo y webmail

Configure los siguientes registros:

```text
A     mail       IP_PUBLICA_VPS
A     webmail    IP_PUBLICA_VPS
MX    @          10 mail.daertechglobal.com
```

SPF inicial:

```text
v=spf1 mx a:mail.daertechglobal.com ip4:IP_PUBLICA_VPS ~all
```

SPF final después de validar todos los emisores:

```text
v=spf1 mx a:mail.daertechglobal.com ip4:IP_PUBLICA_VPS -all
```

DMARC inicial:

```text
v=DMARC1; p=none; pct=100; adkim=s; aspf=s; rua=mailto:dmarc@daertechglobal.com
```

Después del período de observación puede cambiarse a `p=quarantine` y posteriormente a `p=reject`.

Configure el PTR/rDNS desde el proveedor del VPS:

```text
IP_PUBLICA_VPS -> mail.daertechglobal.com
```

Verifique:

```bash
dig +short mail.daertechglobal.com A
dig +short webmail.daertechglobal.com A
dig +short MX daertechglobal.com
dig +short TXT daertechglobal.com
dig +short TXT _dmarc.daertechglobal.com
dig +short -x IP_PUBLICA_VPS
```

## 10. Hostname del VPS

```bash
sudo hostnamectl set-hostname mail.daertechglobal.com
hostname
hostname -f
```

Edite `/etc/hosts`:

```text
127.0.0.1 localhost
IP_PUBLICA_VPS mail.daertechglobal.com mail
```

## 11. Firewall

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing

sudo ufw allow from IP_ADMINISTRATIVA/32 to any port 22 proto tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 25/tcp
sudo ufw allow 465/tcp
sudo ufw allow 587/tcp
sudo ufw allow 993/tcp
sudo ufw allow 4190/tcp

sudo ufw enable
sudo ufw status verbose
```

No publique directamente:

- MariaDB `3306`.
- Roundcube `80` interno.
- Prometheus `9090`.
- Loki `3100`.
- Node Exporter `9100`.
- cAdvisor `8080`.
- Rspamd WebUI `11334`.

## 12. Certificado TLS del servidor de correo

Traefik administra el certificado HTTPS de `webmail.daertechglobal.com`.

Postfix y Dovecot necesitan un certificado PEM accesible en `/etc/letsencrypt` para `mail.daertechglobal.com`.

Detenga temporalmente Traefik si usa el desafío HTTP standalone:

```bash
cd /opt/infra-platform/proxy
docker compose stop traefik
```

Emita el certificado:

```bash
sudo certbot certonly \
  --standalone \
  --preferred-challenges http \
  -d mail.daertechglobal.com \
  --email admin@daertechglobal.com \
  --agree-tos \
  --no-eff-email
```

Inicie Traefik nuevamente:

```bash
docker compose start traefik
```

Verifique:

```bash
sudo ls -la /etc/letsencrypt/live/mail.daertechglobal.com/
```

Cree el hook `/etc/letsencrypt/renewal-hooks/deploy/restart-mailserver.sh`:

```bash
#!/bin/bash
set -e
cd /opt/infra-platform/mail
/usr/bin/docker compose restart mailserver
```

```bash
sudo chmod 750 /etc/letsencrypt/renewal-hooks/deploy/restart-mailserver.sh
sudo certbot renew --dry-run
```

## 13. Directorios persistentes

```bash
cd /opt/infra-platform

mkdir -p \
  mail/data/mail \
  mail/data/state \
  mail/logs \
  mail/config \
  mail/data/roundcube-db \
  mail/roundcube/config \
  mail/roundcube/temp

chmod 700 mail/config
```

## 14. Implementación de docker-mailserver + Roundcube

El archivo `mail/docker-compose.yml` incluye:

- `mailserver`: SMTP, Submission, IMAPS y ManageSieve.
- `roundcube-db`: MariaDB interna y persistente.
- `roundcube`: cliente web publicado por Traefik.

Valide el Compose:

```bash
cd /opt/infra-platform

docker compose --env-file .env \
  -f mail/docker-compose.yml config
```

Levante el módulo:

```bash
docker compose --env-file .env \
  -f mail/docker-compose.yml pull

docker compose --env-file .env \
  -f mail/docker-compose.yml up -d

docker compose --env-file .env \
  -f mail/docker-compose.yml ps
```

Revise logs:

```bash
docker compose --env-file .env \
  -f mail/docker-compose.yml logs --tail=200 mailserver

docker compose --env-file .env \
  -f mail/docker-compose.yml logs --tail=200 roundcube

docker compose --env-file .env \
  -f mail/docker-compose.yml logs --tail=200 roundcube-db
```

## 15. Crear cuentas y alias

```bash
cd /opt/infra-platform

MAIL_COMPOSE='docker compose --env-file .env -f mail/docker-compose.yml'

$MAIL_COMPOSE exec mailserver setup email add admin@daertechglobal.com
$MAIL_COMPOSE exec mailserver setup email add postmaster@daertechglobal.com
$MAIL_COMPOSE exec mailserver setup email add dmarc@daertechglobal.com

$MAIL_COMPOSE exec mailserver \
  setup alias add abuse@daertechglobal.com admin@daertechglobal.com

$MAIL_COMPOSE exec mailserver \
  setup alias add hostmaster@daertechglobal.com admin@daertechglobal.com

$MAIL_COMPOSE exec mailserver setup email list
```

El usuario de Roundcube es la dirección completa:

```text
admin@daertechglobal.com
```

## 16. Generar DKIM

```bash
cd /opt/infra-platform

docker compose --env-file .env \
  -f mail/docker-compose.yml exec mailserver \
  setup config dkim domain daertechglobal.com
```

Busque el registro generado:

```bash
find mail/config -type f \
  \( -iname '*.txt' -o -iname '*.dns' \) -print
```

Publique el TXT DKIM exactamente como fue generado. El selector suele ser `mail`, pero debe verificarse en el archivo resultante.

## 17. Acceso a Roundcube

Acceda a:

```text
https://webmail.daertechglobal.com
```

Roundcube se conecta internamente a:

```text
IMAPS: mail.daertechglobal.com:993
SMTP Submission: mail.daertechglobal.com:587
ManageSieve: mail.daertechglobal.com:4190
```

MariaDB no debe estar expuesta públicamente.

## 18. Pruebas

Comprobar HTTPS:

```bash
curl -I https://webmail.daertechglobal.com
```

Comprobar IMAPS:

```bash
openssl s_client \
  -connect mail.daertechglobal.com:993 \
  -servername mail.daertechglobal.com
```

Comprobar SMTP STARTTLS:

```bash
openssl s_client \
  -starttls smtp \
  -connect mail.daertechglobal.com:587 \
  -servername mail.daertechglobal.com
```

Comprobar puertos:

```bash
nc -vz mail.daertechglobal.com 25
nc -vz mail.daertechglobal.com 465
nc -vz mail.daertechglobal.com 587
nc -vz mail.daertechglobal.com 993
nc -vz mail.daertechglobal.com 4190
```

Comprobar estado:

```bash
docker compose --env-file .env \
  -f mail/docker-compose.yml ps
```

## 19. Monitoreo y logs

Supervise como mínimo:

- Estado de `mailserver`, `roundcube` y `roundcube-db`.
- Disponibilidad de 25, 587, 993 y HTTPS.
- Cola de Postfix.
- Autenticaciones fallidas.
- Uso de disco en buzones y MariaDB.
- Vencimiento de certificados.
- Estado de Rspamd y Dovecot.
- Errores HTTP/PHP de Roundcube.

Comandos operativos:

```bash
docker logs --tail=200 infra-mailserver
docker logs --tail=200 infra-roundcube
docker logs --tail=200 infra-roundcube-db

docker exec infra-mailserver postqueue -p
docker stats --no-stream
```

Los logs persistentes del correo se encuentran en:

```text
/opt/infra-platform/mail/logs
```

Deben ser recolectados por Loki mediante Alloy o Promtail.

## 20. Respaldo

Respaldar:

```text
/opt/infra-platform/mail/data/mail
/opt/infra-platform/mail/data/state
/opt/infra-platform/mail/config
/opt/infra-platform/mail/roundcube/config
/etc/letsencrypt
```

Para MariaDB, genere un dump consistente:

```bash
cd /opt/infra-platform
set -a
source .env
set +a

docker compose --env-file .env \
  -f mail/docker-compose.yml exec -T roundcube-db \
  mariadb-dump \
  -u root \
  -p"$ROUNDCUBE_DB_ROOT_PASSWORD" \
  --single-transaction \
  --routines \
  --triggers \
  "$ROUNDCUBE_DB_NAME" \
  > /tmp/roundcubemail.sql
```

Incluya el dump y las rutas persistentes en Restic:

```bash
restic backup \
  /tmp/roundcubemail.sql \
  /opt/infra-platform/mail/data/mail \
  /opt/infra-platform/mail/data/state \
  /opt/infra-platform/mail/config \
  /opt/infra-platform/mail/roundcube/config \
  /etc/letsencrypt
```

Verifique regularmente la restauración, no solo la creación del respaldo.

## 21. Actualización controlada

```bash
cd /opt/infra-platform

git rev-parse HEAD
git fetch --all --prune
git checkout TAG_O_COMMIT_APROBADO

./scripts/validate.sh

docker compose --env-file .env \
  -f mail/docker-compose.yml config

docker compose --env-file .env \
  -f mail/docker-compose.yml pull

docker compose --env-file .env \
  -f mail/docker-compose.yml up -d
```

Antes de actualizar:

1. Realizar respaldo.
2. Registrar el SHA actual.
3. Validar el nuevo Compose.
4. Revisar cambios de versión de MariaDB y Roundcube.
5. Mantener disponible el commit anterior para rollback.

## 22. Checklist de producción

- [ ] Debian actualizado y endurecido.
- [ ] Acceso SSH con llaves.
- [ ] `PermitRootLogin no`.
- [ ] `PasswordAuthentication no` después de validar las llaves.
- [ ] Firewall activo.
- [ ] Puerto 25 de salida habilitado por el proveedor.
- [ ] DNS A, MX, SPF, DKIM y DMARC publicados.
- [ ] PTR/rDNS configurado.
- [ ] Certificado de `mail.daertechglobal.com` válido.
- [ ] Certificado HTTPS de `webmail.daertechglobal.com` válido.
- [ ] Variables reales aplicadas en `.env`.
- [ ] Contraseñas fuertes y distintas.
- [ ] `docker compose config` sin errores.
- [ ] Roundcube accesible por HTTPS.
- [ ] IMAPS 993 operativo.
- [ ] SMTP 587 autenticado operativo.
- [ ] Envío y recepción probados con proveedores externos.
- [ ] Logs visibles en Loki/Grafana.
- [ ] Alertas activas.
- [ ] Respaldo y restauración probados.

## 23. Resultado final

```text
Webmail:
https://webmail.daertechglobal.com

Servidor entrante:
mail.daertechglobal.com
Puerto: 993
Seguridad: SSL/TLS

Servidor saliente:
mail.daertechglobal.com
Puerto: 587
Seguridad: STARTTLS
Autenticación: obligatoria

Usuario:
correo-completo@daertechglobal.com
```

Roundcube queda integrado como parte oficial del módulo `mail`, protegido por Traefik y conectado internamente a `docker-mailserver`, mientras MariaDB permanece aislada en la red privada.