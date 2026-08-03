# Fase 2B — Validación y exportación

## Alcance

Esta entrega completa el bloque operativo inicial del Configuration Center.

## Interfaz Angular

La pantalla de configuración permite:

- Seleccionar ambiente.
- Registrar categoría, clave, tipo y valor.
- Marcar valores secretos.
- Definir una expresión regular de validación.
- Activar o desactivar configuraciones.
- Indicar el motivo del cambio.
- Consultar configuraciones existentes.
- Probar conectividad.
- Descargar archivos `.env` y YAML.

## Validadores

Tipos soportados:

- HTTP y HTTPS.
- REST y SOAP.
- PostgreSQL y MySQL mediante prueba TCP.
- Redis.
- RabbitMQ.
- MQTT.
- SMTP.
- MinIO.
- Telegram.
- TCP genérico.

La validación usa un timeout entre 500 ms y 30 segundos. Las pruebas verifican disponibilidad de red; no sustituyen una autenticación funcional completa de cada proveedor.

## Exportaciones

### `.env`

```http
GET /api/v1/admin/configuration-operations/export?environment=PRODUCTION&format=ENV
```

### YAML

```http
GET /api/v1/admin/configuration-operations/export?environment=PRODUCTION&format=YAML
```

Los secretos se enmascaran por defecto. El parámetro `includeSecrets=true` solo debe utilizarse desde una sesión administrativa controlada y nunca debe enviarse a logs, tickets ni repositorios.

## Cambios manuales

1. Configurar `CONFIG_ENCRYPTION_KEY` antes de guardar el primer secreto.
2. Mantener permanentemente la misma clave o ejecutar una migración de recifrado.
3. Restringir `CONFIG_WRITE` a administradores de infraestructura.
4. Evitar exponer los endpoints de validación directamente a Internet.
5. Validar los archivos exportados antes de aplicarlos en producción.
6. Guardar una copia de respaldo antes de reemplazar `.env` o YAML existentes.

## Próximo bloque

- Plantillas de configuración por servicio.
- Perfiles reutilizables por ambiente.
- Aplicación controlada de cambios.
- Backup previo y rollback de archivos.
- Notificación por Telegram.
