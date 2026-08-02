# Fase 1B — Seguridad y administración

## Alcance implementado

- Autenticación JWT y refresh tokens.
- RBAC con usuarios, roles y permisos.
- CRUD REST de usuarios y roles.
- Consulta de permisos.
- Auditoría automática para operaciones `/admin/**`.
- Manejo global de errores y validaciones.
- Interfaz Angular de login y consulta administrativa.

## Endpoints

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET|POST /api/v1/admin/users`
- `PUT|DELETE /api/v1/admin/users/{id}`
- `GET|POST /api/v1/admin/roles`
- `PUT|DELETE /api/v1/admin/roles/{id}`
- `GET /api/v1/admin/permissions`

## Cambios manuales

1. Configure `APP_JWT_SECRET` con al menos 64 caracteres.
2. Configure `APP_ADMIN_PASSWORD` con al menos 12 caracteres.
3. Mantenga `.env` fuera de Git.
4. No exponga PostgreSQL, Redis ni Actuator públicamente.
5. Después del primer inicio de sesión, cambie la contraseña temporal del administrador.
6. Revise `platform.audit_events` para confirmar el registro de operaciones administrativas.

## Validación mínima

```bash
curl -fsS http://127.0.0.1:8080/api/v1/actuator/health
curl -sS -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"SU_PASSWORD"}'
```

Use el access token resultante para consultar `/api/v1/admin/users`, `/roles` y `/permissions`.
