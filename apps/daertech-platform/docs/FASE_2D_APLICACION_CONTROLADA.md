# Fase 2D — Aplicación controlada de configuraciones

## Alcance implementado

- Escritura restringida a un directorio raíz permitido.
- Rechazo de rutas absolutas y de recorridos `..` fuera del directorio autorizado.
- Backup automático antes de reemplazar un archivo existente.
- Escritura mediante archivo temporal y movimiento atómico cuando el sistema de archivos lo permite.
- Cálculo SHA-256 antes y después del cambio.
- Historial persistente de cada aplicación y rollback.
- Health check HTTP opcional después de aplicar.
- Rollback automático cuando falla la escritura, el reinicio o el health check.
- Rollback manual usando el identificador de la operación.
- Lista blanca de servicios permitidos para reinicio.
- Reinicio deshabilitado por defecto.

## Endpoints

```text
POST /api/v1/admin/configuration-apply
GET  /api/v1/admin/configuration-apply/history
POST /api/v1/admin/configuration-apply/{operationId}/rollback
```

## Ejemplo de aplicación

```json
{
  "templateCode": "PROMETHEUS",
  "environment": "PRODUCTION",
  "relativePath": "prometheus/prometheus.yml",
  "content": "global:\n  scrape_interval: 15s\n",
  "serviceName": "prometheus",
  "restartService": false,
  "healthUrl": "http://prometheus:9090/-/ready",
  "reason": "Actualizar intervalo de recolección"
}
```

## Directorios

```text
CONFIG_ROOT_DIRECTORY=/opt/infra-platform/generated
CONFIG_BACKUP_DIRECTORY=/opt/infra-platform/backups/configuration
```

En Docker Compose se montan como bind mounts:

```text
./generated:/opt/infra-platform/generated
./backups/configuration:/opt/infra-platform/backups/configuration
```

## Reinicio de servicios

El reinicio permanece deshabilitado por defecto:

```text
CONFIG_RESTART_ENABLED=false
```

Para habilitarlo, el contenedor debe disponer del cliente Docker, acceso al proyecto Compose y permisos explícitos sobre el daemon. No se incluye el montaje de `/var/run/docker.sock` por defecto porque concede privilegios elevados al contenedor. Debe evaluarse y aprobarse como cambio manual de seguridad.

## Controles manuales antes de producción

1. Crear los directorios persistentes y asignar permisos al UID del backend.
2. Confirmar que `CONFIG_ROOT_DIRECTORY` no apunta a `/`, `/etc`, `/var` ni a un directorio general del host.
3. Mantener `CONFIG_RESTART_ENABLED=false` hasta completar pruebas y hardening.
4. Configurar health checks internos alcanzables desde la red Docker.
5. Verificar espacio y retención del directorio de backups.
6. Respaldar la base PostgreSQL antes de activar aplicaciones automáticas.
7. Limitar el permiso `CONFIG_WRITE` exclusivamente a administradores autorizados.

## Limitaciones actuales

- El contenido se recibe ya renderizado; la unión directa con el catálogo de plantillas se completará en el flujo de despliegue.
- El reinicio usa `docker compose restart <servicio>` y requiere preparación manual del contenedor o un agente de ejecución separado.
- Los backups no tienen todavía política automática de retención.
