# Infra Platform

Plataforma Docker para VPS con proxy reverso, observabilidad, logs, respaldos, mensajería y administración.

## Inicio rápido

```bash
cp .env.example .env
chmod +x scripts/*.sh
./scripts/bootstrap.sh
./scripts/up.sh proxy
```

## Módulos

- `proxy`: Traefik, HTTPS y métricas.
- `management`: Portainer y Uptime Kuma.
- `monitoring`: Prometheus, Grafana, Alertmanager, Node Exporter, cAdvisor y Blackbox Exporter.
- `logging`: Loki, Alloy y Dozzle.
- `backups`: Restic y Rclone.

Los módulos se incorporarán por fases manteniendo archivos ejecutables y configuración desacoplada mediante `.env`.
