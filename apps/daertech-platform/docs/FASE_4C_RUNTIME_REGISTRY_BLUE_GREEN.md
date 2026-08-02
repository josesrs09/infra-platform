# Fase 4C — Runtime de despliegues, registry y blue/green

## Alcance

Esta fase agrega operaciones explícitas y auditables para publicar imágenes en un registro privado, consultar eventos operativos y conmutar tráfico entre slots `BLUE` y `GREEN` mediante archivos dinámicos de Traefik.

## Endpoints

```text
GET  /api/v1/admin/deployment-runtime/{deploymentId}/events
POST /api/v1/admin/deployment-runtime/{deploymentId}/push
POST /api/v1/admin/deployment-runtime/{deploymentId}/switch-traffic
```

Cuerpo para conmutar tráfico:

```json
{
  "targetSlot": "GREEN"
}
```

## Cambios manuales obligatorios

### 1. Mantener la ejecución deshabilitada inicialmente

```env
DEPLOYMENT_EXECUTION_ENABLED=false
```

Active esta opción únicamente en DEVELOPMENT o QA después de completar todas las validaciones.

### 2. Preparar el directorio dinámico de Traefik

```bash
mkdir -p ./traefik/dynamic
chmod 750 ./traefik/dynamic
```

El mismo directorio debe estar montado en Traefik como proveedor de archivos:

```yaml
providers:
  file:
    directory: /etc/traefik/dynamic
    watch: true
```

En el contenedor de Traefik debe montarse:

```yaml
volumes:
  - ./traefik/dynamic:/etc/traefik/dynamic:ro
```

### 3. Crear servicios blue y green

Los servicios deben seguir la convención:

```text
<application-code>-<environment>-blue
<application-code>-<environment>-green
```

Ejemplo:

```text
clientes-api-production-blue
clientes-api-production-green
```

Ambos servicios deben permanecer disponibles durante la conmutación. No elimine el slot anterior hasta terminar las verificaciones funcionales.

### 4. Configurar el registro privado

En el catálogo de registros habilite `push_enabled=true` únicamente después de probar manualmente:

```bash
docker login registry.daertechglobal.com
docker pull registry.daertechglobal.com/daertech/prueba:latest
docker push registry.daertechglobal.com/daertech/prueba:latest
```

Las credenciales deben guardarse como secretos del Configuration Center para cada ambiente:

```text
REGISTRY_DAERTECH_USERNAME
REGISTRY_DAERTECH_PASSWORD
```

No incluya contraseñas en Docker Compose, Git, logs o parámetros de URL.

### 5. Permisos

Asigne de manera restringida:

```text
DEPLOYMENT_REGISTRY_PUSH
DEPLOYMENT_TRAFFIC_SWITCH
```

La consulta de eventos requiere `DEPLOYMENT_READ`.

### 6. Telegram

Configure:

```env
TELEGRAM_BOT_TOKEN=...
TELEGRAM_CHAT_ID=...
```

Se notifican publicaciones exitosas o fallidas y conmutaciones de tráfico.

## Secuencia recomendada

1. Crear el despliegue.
2. Ejecutar build y despliegue en el slot inactivo.
3. Confirmar health check.
4. Publicar la imagen al registry.
5. Ejecutar pruebas funcionales contra el slot inactivo.
6. Conmutar Traefik al nuevo slot.
7. Verificar URL pública, logs y métricas.
8. Mantener el slot anterior disponible durante la ventana de observación.
9. Retirar el slot anterior solo después de la aprobación operativa.

## Recuperación

Para volver al slot anterior:

```http
POST /api/v1/admin/deployment-runtime/{deploymentId}/switch-traffic
Content-Type: application/json

{"targetSlot":"BLUE"}
```

La operación reemplaza de forma atómica el archivo dinámico cuando el sistema de archivos lo permite. En otros casos utiliza reemplazo seguro no atómico.

## Riesgos

- El socket Docker concede control elevado sobre el host.
- Una configuración incorrecta de Traefik puede interrumpir tráfico.
- El registro debe utilizar TLS válido; registros inseguros requieren configuración adicional del daemon Docker.
- La conmutación no reemplaza las pruebas de base de datos, compatibilidad de contratos ni migraciones reversibles.

## Validación

```bash
docker compose exec backend docker version
docker compose exec backend docker compose version
docker compose exec backend ls -la /opt/infra-platform/traefik/dynamic
curl -fsS https://api-infra.daertechglobal.com/api/v1/actuator/health
```

Mantenga `DEPLOYMENT_EXECUTION_ENABLED=false` si cualquiera de estas verificaciones falla.
