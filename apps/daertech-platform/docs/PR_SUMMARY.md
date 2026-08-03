# PR Summary — DAERTECH Platform

## Plataforma

- Spring Boot 3.5 / Java 21.
- Angular 20.
- PostgreSQL 17 y Redis 8.
- Docker Compose.

## Centros funcionales

- Seguridad administrativa con JWT, refresh tokens, RBAC y auditoría.
- Configuration Center con secretos cifrados, historial, validación, exportación y rollback.
- Applications Center con catálogo, ambientes, variables, dependencias y versiones.
- Deployment Center con solicitudes, ejecución, pasos, health checks, rollback, estrategias, registry, promoción y blue/green.
- Monitoring Center con objetivos, verificaciones, Prometheus, Grafana, Loki y Alertmanager.

## Frontend modular

El shell Angular dispone de rutas standalone para:

- Dashboard.
- Usuarios.
- Roles.
- Permisos.
- Configuración.
- Aplicaciones.
- Despliegues.
- Monitoreo.

La ruta `/legacy` se conserva temporalmente hasta activar el nuevo bootstrap y validar paridad funcional.

## Bloqueos antes de producción

- Compilar y probar backend y frontend.
- Corregir versiones duplicadas de migraciones Flyway.
- Validar el arranque completo con PostgreSQL limpio.
- Mantener la ejecución de despliegues deshabilitada hasta completar pruebas controladas.
- Separar el acceso al socket Docker en un agente de ejecución dedicado.
- Configurar secretos, certificados, firewall, Traefik y retención de observabilidad.
