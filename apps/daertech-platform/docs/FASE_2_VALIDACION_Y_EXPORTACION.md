# Fase 2B — Validación y exportación de configuración

## Alcance implementado

- Formulario Angular dinámico para registrar configuraciones por ambiente.
- Tipos de valor: `STRING`, `NUMBER`, `BOOLEAN`, `URL`, `JSON`, `PASSWORD`, `TOKEN` y `CERTIFICATE`.
- Enmascaramiento de secretos en listados.
- Exportación de configuraciones en formato `.env` y YAML.
- Validación de conectividad HTTP/HTTPS y TCP.
- Perfiles de validación para PostgreSQL, MySQL, Redis, RabbitMQ, MQTT, SMTP, MinIO, REST, SOAP y Telegram.
- Tiempo máximo configurable entre 500 ms y 30 segundos.

## Endpoints

```text
POST /api/v1/admin/configuration-operations/validate
GET  /api/v1/admin/configuration-operations/export?environment=PRODUCTION&format=ENV
GET  /api/v1/admin/configuration-operations/export?environment=PRODUCTION&format=YAML
```

## Ejemplo de validación

```json
{
  "type": "POSTGRESQL",
  "host": "postgres",
  "port": 5432,
  "timeoutMs": 5000
}
```

Para HTTP:

```json
{
  "type": "REST",
  "scheme": "https",
  "host": "api.example.com",
  "port": 443,
  "path": "/actuator/health",
  "method": "GET",
  "timeoutMs": 5000,
  "headers": {
    "X-API-KEY": "valor-temporal"
  }
}
```

## Exportación de secretos

La API exporta secretos enmascarados por defecto. El parámetro `includeSecrets=true` permite descifrarlos, pero debe utilizarse únicamente en procesos administrativos controlados y nunca exponerse directamente en enlaces públicos, logs o auditorías.

## Limitación actual

Los perfiles PostgreSQL, Redis, RabbitMQ, MQTT, SMTP y MinIO validan apertura de puerto TCP. La autenticación específica por protocolo, TLS, credenciales y comandos funcionales se incorporará en el siguiente bloque de validadores avanzados.
