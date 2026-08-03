# Fase 2C — Plantillas, aplicación controlada y rollback

## Alcance

Esta fase agrega plantillas reutilizables, perfiles por ambiente, aplicación controlada sobre archivos administrados, respaldo automático, verificación SHA-256, rollback y notificaciones Telegram.

## Seguridad de rutas

La aplicación únicamente puede escribir dentro de:

```text
APP_CONFIG_ALLOWED_ROOT=/opt/daertech/config
```

Cualquier ruta absoluta o relativa que salga de ese directorio será rechazada. En Docker se montan:

```text
./generated              -> /opt/daertech/config/generated
./managed-config         -> /opt/daertech/config/managed
./backups/configuration  -> /opt/daertech/config/backups
```

No debe montarse `/`, `/etc`, `/var/run/docker.sock` ni directorios generales del host dentro de esta función.

## Flujo de aplicación

1. Crear una plantilla con variables `${CLAVE}`.
2. Crear un perfil indicando ambiente, plantilla y ruta destino.
3. Confirmar que todas las configuraciones requeridas existen.
4. Ejecutar la aplicación con un motivo obligatorio.
5. Si existe un archivo destino, se crea un respaldo previo.
6. El nuevo archivo se escribe primero en un archivo temporal.
7. Se realiza movimiento atómico hacia el destino.
8. Se registran checksums SHA-256 anterior y posterior.
9. Se registra usuario, fecha, ambiente, ruta y estado.
10. Se envía notificación Telegram cuando está configurado.

## API

```text
GET  /api/v1/admin/configuration-management/templates
POST /api/v1/admin/configuration-management/templates
GET  /api/v1/admin/configuration-management/profiles
POST /api/v1/admin/configuration-management/profiles
POST /api/v1/admin/configuration-management/profiles/{id}/apply
GET  /api/v1/admin/configuration-management/history
POST /api/v1/admin/configuration-management/history/{id}/rollback
```

## Permisos

```text
CONFIG_READ
CONFIG_TEMPLATE
CONFIG_APPLY
```

`CONFIG_APPLY` debe asignarse únicamente a administradores de infraestructura autorizados.

## Cambios manuales

Crear los directorios y aplicar permisos al usuario que ejecuta Docker:

```bash
mkdir -p generated managed-config backups/configuration
chmod 750 generated managed-config backups/configuration
```

Configurar Telegram en `.env`:

```dotenv
TELEGRAM_BOT_TOKEN=<token-real>
TELEGRAM_CHAT_ID=<chat-id-real>
```

Los secretos no deben confirmarse en Git.

## Rollback

El rollback restaura exactamente el archivo guardado antes de la aplicación. No reinicia automáticamente servicios. Después del rollback se debe validar la sintaxis y ejecutar manualmente la recarga o reinicio del servicio correspondiente.

## Limitaciones deliberadas

- No ejecuta comandos de shell.
- No reinicia contenedores.
- No escribe fuera del directorio permitido.
- No aplica configuraciones directamente a `/etc`.
- Una falla de Telegram no revierte un cambio ya aplicado.
