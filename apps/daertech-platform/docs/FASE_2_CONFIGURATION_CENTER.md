# Fase 2 — Configuration Center

## Alcance implementado

- Ambientes `DEVELOPMENT`, `QA`, `CERTIFICATION` y `PRODUCTION`.
- Catálogo de configuraciones por categoría, clave y ambiente.
- Tipos: `STRING`, `NUMBER`, `BOOLEAN`, `URL`, `JSON`, `PASSWORD`, `TOKEN` y `CERTIFICATE`.
- Validación opcional mediante expresión regular.
- Cifrado AES-256-GCM para valores marcados como secretos.
- Enmascaramiento de secretos en las respuestas REST.
- Historial versionado de creación, actualización y reversión.
- Rollback a una versión histórica.
- Autorización mediante `CONFIG_READ` y `CONFIG_WRITE`.
- Auditoría general de las operaciones `/admin/**`.

## API

```text
GET  /api/v1/admin/configurations?environment=PRODUCTION
POST /api/v1/admin/configurations
GET  /api/v1/admin/configurations/{id}/history
POST /api/v1/admin/configurations/{id}/rollback/{version}
GET  /api/v1/admin/configurations/environments
```

## Ejemplo de creación

```json
{
  "category": "TELEGRAM",
  "key": "TELEGRAM.BOT_TOKEN",
  "value": "valor-real",
  "secret": true,
  "environment": "PRODUCTION",
  "valueType": "TOKEN",
  "description": "Token del bot de alertas",
  "validationRule": ".{20,}",
  "active": true,
  "reason": "Configuración inicial"
}
```

## Cambio manual obligatorio

Definir una clave estable y exclusiva por instalación:

```bash
CONFIG_ENCRYPTION_KEY=$(openssl rand -base64 48)
```

No debe cambiarse después de almacenar secretos. Si se pierde, los valores cifrados existentes no podrán recuperarse. No debe guardarse en Git.

## Limitaciones actuales

- La API administra y versiona la configuración, pero todavía no genera archivos `.env`, YAML o configuraciones específicas de cada servicio.
- Las pruebas de conectividad por tipo de servicio se incorporarán en el siguiente bloque.
- La pantalla Angular específica del Configuration Center se completará junto con los formularios dinámicos y validadores por proveedor.
