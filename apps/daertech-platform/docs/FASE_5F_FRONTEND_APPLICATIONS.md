# Fase 5F — Frontend modular del Applications Center

## Alcance

Se migró Applications Center a componentes standalone y se completó la administración de dependencias y recursos secundarios.

## Ruta

```text
/applications
```

## Funciones

- Crear, editar y eliminar aplicaciones.
- Configurar repositorio, rama, tecnología, herramienta de build, Dockerfile y contexto.
- Registrar puerto interno, health path y metrics path.
- Administrar ambientes, URL pública, réplicas y límites de recursos.
- Administrar variables por ambiente con soporte para secretos enmascarados.
- Administrar dependencias técnicas.
- Registrar y eliminar versiones, commits e imágenes.

## API complementada

```text
POST   /api/v1/admin/applications/{id}/dependencies
DELETE /api/v1/admin/applications/{id}/dependencies/{dependencyId}
DELETE /api/v1/admin/applications/{id}/environments/{environmentId}
DELETE /api/v1/admin/applications/{id}/variables/{variableId}
DELETE /api/v1/admin/applications/{id}/versions/{versionId}
```

## Seguridad

- `APPLICATION_READ` para consulta.
- `APPLICATION_WRITE` para cambios.
- Los valores secretos se presentan como `********`.
- Al editar una variable secreta debe escribirse un valor nuevo; el frontend no intenta reutilizar el valor enmascarado.

## Pruebas manuales

1. Crear una aplicación y validar el código en mayúsculas.
2. Editar repositorio, tecnología, puerto y endpoints.
3. Agregar ambientes DEVELOPMENT y PRODUCTION.
4. Agregar una variable normal y otra secreta.
5. Agregar una dependencia DATABASE o API.
6. Registrar una versión con commit e imagen.
7. Eliminar cada recurso secundario y confirmar que el detalle se actualiza.
8. Verificar acceso con usuario de solo lectura y con usuario sin permisos.

## Pendiente

- Compilación real de Angular.
- Validación de formularios más estricta.
- Confirmaciones modales reutilizables.
- Integración posterior con Deployment Center para seleccionar versiones y ambientes.
