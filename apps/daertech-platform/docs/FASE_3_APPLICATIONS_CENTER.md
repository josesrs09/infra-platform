# Fase 3 — Applications Center

## Objetivo

Centralizar el inventario técnico de aplicaciones administradas por DAERTECH Platform antes de automatizar compilaciones y despliegues.

## Capacidades

- Registro de aplicaciones y repositorios Git.
- Rama principal, tecnología, herramienta de build, Dockerfile y contexto de construcción.
- Puerto interno, endpoint de salud y endpoint de métricas.
- Configuración por ambiente: rama, URL pública, réplicas y límites de recursos.
- Variables por aplicación y ambiente, con indicador de secreto y obligatoriedad.
- Dependencias de infraestructura o aplicaciones.
- Historial de versiones, commit Git, imagen y notas.
- Permisos separados de lectura y escritura.

## Endpoints

```text
GET    /api/v1/admin/applications
GET    /api/v1/admin/applications/{id}
POST   /api/v1/admin/applications
DELETE /api/v1/admin/applications/{id}
POST   /api/v1/admin/applications/{id}/environments
POST   /api/v1/admin/applications/{id}/variables
POST   /api/v1/admin/applications/{id}/versions
GET    /api/v1/admin/applications/catalog/technologies
```

## Cambios manuales

1. Registrar cada repositorio con una URL accesible desde el servidor de despliegue.
2. Crear credenciales Git de solo lectura o deploy keys; no almacenar tokens en texto plano en Git.
3. Confirmar la rama de producción y la ruta real del Dockerfile.
4. Definir el puerto interno del contenedor, no el puerto publicado del host.
5. Configurar un health endpoint que responda sin autenticación desde la red interna.
6. Configurar métricas Prometheus cuando la aplicación las exponga.
7. Registrar variables sensibles como secretos y completar sus valores únicamente en el entorno seguro.
8. Verificar DNS y certificados de las URL públicas por ambiente.

## Próxima fase

Deployment Center: checkout controlado, build, versionado de imágenes, ejecución Docker Compose, health check, historial, rollback y notificaciones Telegram.
