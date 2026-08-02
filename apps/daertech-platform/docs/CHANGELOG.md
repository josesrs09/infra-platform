# Changelog

## 0.4.0 — Plantillas y aplicación controlada

- Plantillas reutilizables por servicio.
- Perfiles de configuración por ambiente.
- Aplicación controlada dentro de un directorio permitido.
- Respaldo automático previo a cada cambio.
- Escritura atómica mediante archivo temporal.
- Checksums SHA-256 antes y después.
- Historial de aplicaciones y rollback de archivos.
- Notificaciones Telegram para aplicación y reversión.
- Permisos `CONFIG_TEMPLATE` y `CONFIG_APPLY`.

## 0.3.0 — Configuration Center operativo

- Configuración por ambiente, categoría y tipo.
- Secretos cifrados con AES-256-GCM.
- Historial, validación y rollback lógico.
- Formularios Angular dinámicos.
- Validadores de conectividad.
- Exportación `.env` y YAML.

## 0.2.0 — Seguridad administrativa

- JWT y refresh tokens con rotación y revocación.
- RBAC basado en PostgreSQL.
- Bootstrap seguro del administrador.
- CRUD REST de usuarios y roles.
- Consulta de permisos.
- Auditoría automática para operaciones administrativas.
- Manejo global de errores y validaciones.
- Interfaz Angular de login, dashboard y consultas de seguridad.

## 0.1.0 — Fundación

- Angular, Spring Boot, PostgreSQL y Redis.
- Docker Compose y bind mounts.
- Flyway, Actuator y Prometheus.
- Documentación inicial de puesta en marcha.
