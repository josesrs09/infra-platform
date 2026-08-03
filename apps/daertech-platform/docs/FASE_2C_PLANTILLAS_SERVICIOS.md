# Fase 2C — Plantillas de servicios

## Alcance implementado

Se incorporó un catálogo persistente de plantillas para generar configuraciones operativas sin editar archivos manualmente.

Plantillas iniciales:

- PostgreSQL (`POSTGRESQL_ENV`).
- Redis (`REDIS_ENV`).
- RabbitMQ (`RABBITMQ_ENV`).
- MQTT (`MQTT_ENV`).
- SMTP (`SMTP_ENV`).
- MinIO (`MINIO_ENV`).
- Traefik dynamic configuration (`TRAEFIK_DYNAMIC_YAML`).
- Prometheus scrape target (`PROMETHEUS_TARGET_YAML`).

## API

```text
GET  /api/v1/admin/service-templates
GET  /api/v1/admin/service-templates/{code}
POST /api/v1/admin/service-templates/{code}/render
POST /api/v1/admin/service-templates/{code}/download
```

Todos los endpoints requieren permisos del Configuration Center.

## Renderizado

Ejemplo para PostgreSQL:

```json
{
  "HOST": "postgres",
  "PORT": "5432",
  "DATABASE": "daertech_platform",
  "USERNAME": "daertech",
  "PASSWORD": "valor-suministrado-en-runtime"
}
```

Ejemplo para Traefik:

```json
{
  "ROUTER": "daertech-api",
  "HOST": "api-infra.daertechglobal.com",
  "SERVICE": "daertech-api",
  "URL": "http://backend:8080"
}
```

## Seguridad

- Las plantillas no contienen secretos reales.
- Los valores se suministran al renderizar.
- El resultado generado no se almacena automáticamente.
- La descarga exige `CONFIG_WRITE`.
- Los valores sensibles no deben enviarse a logs, comentarios de GitHub ni documentación.

## Cambios manuales pendientes para producción

1. Revisar los nombres de variables requeridos por cada servicio real.
2. Confirmar puertos y hostnames internos de Docker.
3. Definir dominios públicos y rutas de Traefik.
4. Mantener secretos únicamente en el VPS o en el Configuration Center cifrado.
5. Validar el archivo generado antes de reemplazar configuraciones activas.
6. Realizar backup de la configuración anterior.
7. Reiniciar únicamente el servicio afectado y ejecutar su health check.

## Siguiente bloque

- Validadores autenticados por protocolo.
- Versionado de plantillas.
- Aplicación controlada de archivos en directorios permitidos.
- Backup automático antes de aplicar.
- Reinicio y health check del servicio afectado.
