# Fase 4 — Deployment Center

## Alcance

El Deployment Center administra solicitudes de despliegue, checkout del repositorio, construcción de imágenes, ejecución de Docker Compose, comprobación de salud, historial, logs y rollback.

## Flujo

1. Registrar la aplicación y su ambiente en Applications Center.
2. Crear una solicitud de despliegue en estado `PENDING`.
3. Ejecutar checkout de la rama configurada.
4. Construir la imagen con el Dockerfile registrado.
5. Ejecutar `docker compose up -d --remove-orphans`.
6. Consultar el health endpoint del ambiente.
7. Marcar el despliegue como `SUCCESS` o `FAILED`.
8. Conservar pasos, comandos, salida y códigos de retorno.

## API

```text
GET  /api/v1/admin/deployments
GET  /api/v1/admin/deployments/{id}
POST /api/v1/admin/deployments
POST /api/v1/admin/deployments/{id}/execute
POST /api/v1/admin/deployments/{id}/rollback
```

## Seguridad

La ejecución real está deshabilitada por defecto:

```env
DEPLOYMENT_EXECUTION_ENABLED=false
```

Mantenerla deshabilitada hasta completar las validaciones manuales. El acceso a `/var/run/docker.sock` equivale prácticamente a control administrativo del host. Solo el rol de operación autorizado debe poseer `DEPLOYMENT_EXECUTE` y `DEPLOYMENT_ROLLBACK`.

## Cambios manuales obligatorios

### 1. Obtener el GID de Docker

```bash
getent group docker
stat -c '%g' /var/run/docker.sock
```

Asignar el resultado:

```env
DOCKER_GID=999
```

### 2. Preparar deploy keys

```bash
mkdir -p secrets/ssh
chmod 700 secrets/ssh
ssh-keygen -t ed25519 -f secrets/ssh/id_ed25519 -C daertech-deployer
chmod 600 secrets/ssh/id_ed25519
chmod 644 secrets/ssh/id_ed25519.pub
ssh-keyscan github.com > secrets/ssh/known_hosts
chmod 644 secrets/ssh/known_hosts
```

Registrar la clave pública como deploy key de solo lectura en cada repositorio privado. No versionar `secrets/ssh`.

### 3. Preparar directorios

```bash
mkdir -p workspaces secrets/ssh
sudo chown -R 100:101 workspaces || true
```

Verificar el UID/GID real del usuario `app` dentro de la imagen antes de ajustar propietarios.

### 4. Verificar herramientas

```bash
docker compose run --rm backend git --version
docker compose run --rm backend docker version
docker compose run --rm backend docker compose version
```

### 5. Validar acceso al socket

```bash
docker compose run --rm backend docker ps
```

Si falla por permisos, corregir `DOCKER_GID`. No ejecutar el contenedor como `root` como solución permanente.

### 6. Activar la ejecución

Solo después de pasar las validaciones:

```env
DEPLOYMENT_EXECUTION_ENABLED=true
```

```bash
docker compose up -d --build backend
```

## Restricciones operativas

- El repositorio debe contener `docker-compose.yml` en su raíz.
- El Dockerfile y build context deben coincidir con Applications Center.
- Las versiones solo aceptan caracteres alfanuméricos, punto, guion y guion bajo.
- Los workspaces se crean bajo `/opt/infra-platform/workspaces`.
- Cada comando tiene timeout configurable, máximo de una hora.
- La salida por paso se limita para evitar crecimiento ilimitado de la base de datos.

## Rollback

El rollback crea una nueva solicitud basada en el último despliegue exitoso anterior. No modifica destructivamente el historial. Después de crearla debe ejecutarse mediante el endpoint `/execute`.

## Checklist

- [ ] Aplicación registrada.
- [ ] Ambiente y URL pública configurados.
- [ ] Health endpoint válido.
- [ ] Dockerfile y Compose validados manualmente.
- [ ] Deploy key de solo lectura instalada.
- [ ] `DOCKER_GID` confirmado.
- [ ] Socket Docker accesible exclusivamente al backend.
- [ ] `DEPLOYMENT_EXECUTION_ENABLED=true` aplicado conscientemente.
- [ ] Primer despliegue realizado en DEVELOPMENT o QA.
- [ ] Rollback probado antes de producción.
