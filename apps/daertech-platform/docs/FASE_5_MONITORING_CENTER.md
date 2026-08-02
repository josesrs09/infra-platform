# Fase 5 — Monitoring Center

## Componentes

- Prometheus para métricas y reglas.
- Grafana para visualización.
- Loki y Promtail para logs Docker.
- Alertmanager para agrupación y entrega de alertas.
- Node Exporter para métricas del host.
- cAdvisor para métricas de contenedores.
- API propia para objetivos, health checks e historial.

## Inicio

```bash
docker compose up -d
docker compose -f docker-compose.monitoring.yml up -d
```

## Variables obligatorias

```env
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=CAMBIAR_CLAVE_FUERTE
PROMETHEUS_RETENTION=30d
PROMETHEUS_PORT=9090
GRAFANA_PORT=3000
ALERTMANAGER_PORT=9093
LOKI_PORT=3100
ALERTMANAGER_WEBHOOK_TOKEN=CAMBIAR_TOKEN_LARGO
```

## API

```text
GET  /api/v1/admin/monitoring/dashboard
GET  /api/v1/admin/monitoring/targets
POST /api/v1/admin/monitoring/targets
POST /api/v1/admin/monitoring/targets/{id}/check
```

## Alertmanager

El webhook interno exige un token compartido. Antes de producción se debe ajustar la URL del receptor para enviar el mismo token configurado en `APP_MONITORING_ALERTMANAGER_WEBHOOK_TOKEN`. No se debe exponer el webhook directamente a Internet.

## Validaciones manuales

1. Confirmar que Prometheus muestra `backend`, `node-exporter` y `cadvisor` como `UP`.
2. Confirmar que Grafana carga las fuentes Prometheus y Loki.
3. Verificar que Promtail puede leer `/var/lib/docker/containers`.
4. Probar una alerta controlada en DEVELOPMENT.
5. Confirmar recepción por Telegram.
6. Restringir los puertos 9090, 9093, 3000 y 3100 mediante firewall o Traefik con autenticación.

## Riesgos

- cAdvisor se ejecuta con privilegios elevados.
- Promtail accede a logs de todos los contenedores.
- Las interfaces de monitoreo pueden revelar información sensible.
- La retención de métricas y logs debe dimensionarse según espacio disponible.
