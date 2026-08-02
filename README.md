# Infra Platform

Plataforma Docker modular para producción con persistencia en carpetas locales, proxy reverso, observabilidad, logs, seguridad, respaldos, mensajería, correo y administración web.

## Inicio rápido

```bash
cp .env.example .env
sudo ./scripts/bootstrap.sh
./scripts/validate.sh
```

Guía completa: [`docs/IMPLEMENTACION-PRODUCCION.md`](docs/IMPLEMENTACION-PRODUCCION.md)

Instrumentación HTTP: [`examples/http-metrics/README.md`](examples/http-metrics/README.md)

## Módulos

- `proxy`: Traefik, HTTPS, middlewares y autenticación administrativa.
- `management`: Homepage, Portainer y Uptime Kuma.
- `databases`: PostgreSQL, MySQL, Redis y visores web.
- `monitoring`: Prometheus, Grafana, Alertmanager, exportadores y alertas Telegram.
- `logging`: Loki, Alloy, Dozzle y detección de errores en logs.
- `messaging`: RabbitMQ, EMQX y puente de errores hacia Telegram.
- `storage`: MinIO.
- `backups`: Restic, copias lógicas, Rclone y Dropbox.
- `security`: CrowdSec y Fail2ban.
- `mail`: servidor SMTP/IMAP.
- `ci-cd`: Gitea y Registry privado.

## Validación

```bash
./scripts/validate.sh
```

Comprueba Docker Compose, Bash, JSON, YAML, variables obligatorias, credenciales débiles evidentes y uso de bind mounts locales.

## Persistencia

Los datos se almacenan dentro de cada módulo, por ejemplo:

```text
databases/data/postgres
monitoring/data/grafana
logging/data/loki
messaging/data/rabbitmq
backups/repository
```

No se utilizan volúmenes Docker nombrados para los servicios persistentes.
