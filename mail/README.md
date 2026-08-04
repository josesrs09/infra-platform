# Mail: docker-mailserver + Roundcube

Este módulo integra un servidor SMTP/IMAP con `docker-mailserver:14.0.0`, webmail Roundcube, MariaDB, Traefik y las redes generales de la plataforma.

## Servicios

- `mailserver`: SMTP, Submission, IMAPS, Rspamd, Fail2ban, DKIM y DMARC.
- `roundcube`: cliente web de correo publicado por Traefik.
- `roundcube-db`: preferencias, contactos y sesiones de Roundcube.

## Variables requeridas

Configura en `../.env`:

```env
MAIL_HOSTNAME=mail
MAIL_DOMAIN=daertechglobal.com
POSTMASTER_ADDRESS=postmaster@daertechglobal.com
WEBMAIL_HOST=webmail.daertechglobal.com

ROUNDCUBE_DB_NAME=roundcubemail
ROUNDCUBE_DB_USER=roundcube
ROUNDCUBE_DB_PASSWORD=CLAVE_SEGURA
ROUNDCUBE_DB_ROOT_PASSWORD=CLAVE_ROOT_SEGURA
```

Genera contraseñas con:

```bash
openssl rand -base64 36
```

## DNS

```text
A    mail       IP_PUBLICA_VPS
A    webmail    IP_PUBLICA_VPS
MX   @          10 mail.daertechglobal.com
TXT  @          v=spf1 mx a:mail.daertechglobal.com -all
```

También se requiere PTR/rDNS apuntando la IP pública a `mail.daertechglobal.com`, DKIM y DMARC.

## Certificado del servidor de correo

Traefik administra HTTPS para Roundcube. Postfix y Dovecot leen directamente el certificado de:

```text
/etc/letsencrypt/live/mail.daertechglobal.com/
```

Ejemplo de emisión:

```bash
sudo certbot certonly --standalone \
  -d mail.daertechglobal.com \
  --email admin@daertechglobal.com \
  --agree-tos \
  --no-eff-email
```

## Redes requeridas

```bash
docker network create infra_proxy
docker network create infra_backend
docker network create infra_monitoring
```

## Arranque

Desde la raíz del repositorio:

```bash
cp .env.example .env
nano .env
cd mail
docker compose config
docker compose pull
docker compose up -d
```

## Crear buzones

```bash
docker compose exec mailserver setup email add admin@daertechglobal.com
docker compose exec mailserver setup email add postmaster@daertechglobal.com
```

Listar buzones:

```bash
docker compose exec mailserver setup email list
```

## Acceso

```text
https://webmail.daertechglobal.com
```

El usuario debe iniciar sesión con la dirección completa, por ejemplo `admin@daertechglobal.com`.

## Puertos públicos

- `25/tcp`: SMTP entre servidores.
- `465/tcp`: Submission con TLS implícito.
- `587/tcp`: Submission con STARTTLS.
- `993/tcp`: IMAPS.
- `4190/tcp`: ManageSieve, solo si se necesita acceso externo.
- `80/443`: Traefik y Roundcube.

MariaDB y el puerto HTTP interno de Roundcube no deben publicarse.

## Pruebas

```bash
openssl s_client -connect mail.daertechglobal.com:993 \
  -servername mail.daertechglobal.com

openssl s_client -starttls smtp \
  -connect mail.daertechglobal.com:587 \
  -servername mail.daertechglobal.com

curl -I https://webmail.daertechglobal.com
```

## Respaldo

Incluye en Restic/Rclone:

```text
mail/data/mail
mail/data/state
mail/config
mail/data/roundcube-db
/etc/letsencrypt
```

Para MariaDB se recomienda realizar primero un `mariadb-dump` consistente.
