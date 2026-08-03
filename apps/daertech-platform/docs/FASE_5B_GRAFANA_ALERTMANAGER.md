# Fase 5B — Grafana y Alertmanager

## Objetivo

Completar la configuración operativa de la pila de observabilidad con un token compartido seguro para Alertmanager y un dashboard Grafana provisionado automáticamente.

## Variables obligatorias

```env
MONITORING_ALERTMANAGER_WEBHOOK_TOKEN=GENERAR_UN_VALOR_ALEATORIO_DE_64_CARACTERES
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=CAMBIAR_POR_UNA_CLAVE_FUERTE
PROMETHEUS_RETENTION=30d
```

El mismo valor de `MONITORING_ALERTMANAGER_WEBHOOK_TOKEN` es entregado al backend y al contenedor Alertmanager mediante Docker Compose. No debe almacenarse directamente dentro del archivo YAML del repositorio.

## Flujo del token

1. Docker Compose inyecta el token en Alertmanager.
2. Al iniciar, el contenedor sustituye `__ALERTMANAGER_WEBHOOK_TOKEN__` en la plantilla.
3. Alertmanager envía el token como `Authorization: Bearer ...`.
4. El backend compara el token con `APP_MONITORING_ALERTMANAGER_WEBHOOK_TOKEN`.
5. Las alertas válidas se reenvían a Telegram.

## Dashboard provisionado

Grafana carga automáticamente `DAERTECH Platform Overview`, que incluye:

- targets disponibles y no disponibles;
- CPU del servidor;
- utilización de disco;
- disponibilidad por target;
- CPU por contenedor;
- memoria JVM del backend;
- logs recientes del backend desde Loki.

## Puesta en marcha

```bash
cp .env.example .env
# Editar secretos antes de continuar.
docker compose up -d
docker compose -f docker-compose.monitoring.yml up -d
```

## Validaciones

```bash
docker compose -f docker-compose.monitoring.yml config
docker compose -f docker-compose.monitoring.yml ps
docker compose -f docker-compose.monitoring.yml logs alertmanager
docker compose -f docker-compose.monitoring.yml logs grafana
```

Comprobar además:

- Prometheus: `http://SERVIDOR:9090/targets`.
- Alertmanager: `http://SERVIDOR:9093`.
- Grafana: `http://SERVIDOR:3000`.
- Loki: `http://SERVIDOR:3100/ready`.

## Seguridad

- No publicar los puertos de monitoreo directamente en Internet.
- Usar firewall o Traefik con TLS y autenticación.
- Cambiar inmediatamente la contraseña inicial de Grafana.
- Rotar el token del webhook si se expone.
- Mantener el acceso de cAdvisor y Promtail limitado al host autorizado.

## Estado de la interfaz Angular

La API del Monitoring Center y el dashboard Grafana están disponibles. La integración visual completa dentro del frontend Angular principal se mantiene como siguiente bloque debido al refactor pendiente del archivo monolítico `main.ts` hacia componentes y servicios independientes.
