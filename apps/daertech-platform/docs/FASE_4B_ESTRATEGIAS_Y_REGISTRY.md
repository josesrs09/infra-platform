# Fase 4B — Estrategias de despliegue y registro privado

## Alcance

Esta fase agrega estrategias de despliegue, promoción entre ambientes y administración de registros privados de imágenes.

## Estrategias

- `RECREATE`: reemplazo directo de la versión ejecutándose.
- `ROLLING`: actualización progresiva. Requiere múltiples réplicas, health checks y un archivo Compose preparado para actualización gradual.
- `BLUE_GREEN`: crea un conjunto paralelo y requiere cambiar el tráfico en Traefik después de validar el entorno nuevo.

La selección de estrategia queda registrada en la solicitud. La ejecución efectiva de `ROLLING` y `BLUE_GREEN` debe validarse con los archivos Compose y reglas Traefik de cada aplicación.

## Registro privado

API:

```text
GET  /api/v1/admin/container-registries
POST /api/v1/admin/container-registries
```

Las credenciales no se guardan directamente en `container_registries`. Se registran las claves lógicas que apuntan al Configuration Center:

```text
REGISTRY_DAERTECH_USERNAME
REGISTRY_DAERTECH_PASSWORD
```

Ambas deben crearse como valores secretos.

## Configuración manual del registro

1. Crear el usuario técnico con permiso mínimo de `push` y `pull`.
2. Crear las credenciales como secretos en el Configuration Center.
3. Registrar la URL del servicio, por ejemplo `registry.daertechglobal.com`.
4. Instalar el certificado raíz en Docker cuando el registro use una CA privada.
5. Probar manualmente:

```bash
docker login registry.daertechglobal.com
docker pull registry.daertechglobal.com/daertech/prueba:latest
docker push registry.daertechglobal.com/daertech/prueba:latest
```

6. No activar `insecure=true` en producción. Esa opción solo debe utilizarse temporalmente en una red de pruebas aislada.

## Promoción entre ambientes

```text
POST /api/v1/admin/deployment-operations/{deploymentId}/promote
```

Ejemplo:

```json
{
  "targetEnvironment": "PRODUCTION",
  "reason": "Versión aprobada en certificación"
}
```

La promoción reutiliza versión, rama, estrategia e imagen del despliegue exitoso origen. No ejecuta automáticamente la solicitud cuando `DEPLOYMENT_EXECUTION_ENABLED=false`.

## Selección de estrategia e imagen

```text
POST /api/v1/admin/deployment-operations/{deploymentId}/strategy
```

```json
{
  "strategy": "BLUE_GREEN",
  "registryId": "UUID_DEL_REGISTRO",
  "registryImage": "registry.daertechglobal.com/daertech/api-clientes:1.5.0"
}
```

## Cambios manuales para blue/green

- Definir servicios separados, por ejemplo `api-blue` y `api-green`.
- Asignar routers Traefik separados.
- Configurar un middleware o archivo dinámico que permita conmutar el servicio activo.
- Ejecutar health check sobre el color inactivo antes de conmutar.
- Mantener la versión anterior hasta completar la ventana de observación.
- Documentar el comando exacto para regresar el tráfico al color previo.

## Cambios manuales para rolling update

Docker Compose por sí solo no ofrece el mismo control de rolling update de Docker Swarm o Kubernetes. Para producción se debe elegir una de estas modalidades:

1. Varias instancias con nombres distintos y conmutación gradual en Traefik.
2. Docker Swarm con `deploy.update_config`.
3. Kubernetes con `Deployment`, readiness probes y estrategia `RollingUpdate`.

Mientras no se configure una de estas modalidades, usar `RECREATE` como estrategia efectiva.

## Seguridad

- Mantener las credenciales del registro fuera de Git.
- No devolver secretos en las respuestas REST.
- Limitar `REGISTRY_WRITE`, `DEPLOYMENT_EXECUTE` y `DEPLOYMENT_ROLLBACK`.
- Mantener auditoría de creación, cambio de estrategia, promoción y rollback.
- Revisar periódicamente tokens y deploy keys.

## Checklist

- [ ] Registro accesible por HTTPS.
- [ ] Certificado válido o CA instalada.
- [ ] Usuario técnico con privilegios mínimos.
- [ ] Secretos registrados en Configuration Center.
- [ ] `docker login`, pull y push probados.
- [ ] Health endpoint funcional.
- [ ] Estrategia validada en DEVELOPMENT y QA.
- [ ] Rollback probado.
- [ ] Conmutación Traefik documentada.
- [ ] Notificaciones Telegram verificadas.
