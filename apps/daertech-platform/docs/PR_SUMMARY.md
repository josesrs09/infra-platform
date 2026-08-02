# Resumen del Pull Request

## Implementado

- Fundación Spring Boot 3.5, Java 21, Angular 20, PostgreSQL 17, Redis 8 y Docker Compose.
- JWT, refresh tokens, revocación, RBAC, usuarios, roles, permisos y auditoría.
- Configuration Center por ambiente, categoría y tipo.
- Secretos cifrados con AES-256-GCM.
- Historial, validación, rollback lógico, exportación `.env` y YAML.
- Validadores HTTP, HTTPS, REST, SOAP, TCP, PostgreSQL, MySQL, Redis, RabbitMQ, MQTT, SMTP, MinIO y Telegram.
- Plantillas y perfiles reutilizables por servicio y ambiente.
- Aplicación controlada dentro de un directorio permitido.
- Respaldo automático, escritura atómica y checksums SHA-256.
- Rollback de archivos administrados.
- Notificaciones Telegram para aplicación y reversión.

## Cambios manuales obligatorios

- Configurar `JWT_SECRET`.
- Configurar `ADMIN_PASSWORD`.
- Configurar y conservar `CONFIG_ENCRYPTION_KEY`.
- Configurar `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID` cuando se requieran alertas.
- Crear y proteger `generated`, `managed-config` y `backups/configuration`.
- Asignar `CONFIG_APPLY` exclusivamente a operadores autorizados.
- Validar sintaxis y recargar manualmente el servicio después de aplicar o revertir archivos.

## Pendiente

- Applications Center.
- Deployment Center.
- Integración completa con observabilidad y respaldos.
- Pruebas automatizadas y CI/CD.
