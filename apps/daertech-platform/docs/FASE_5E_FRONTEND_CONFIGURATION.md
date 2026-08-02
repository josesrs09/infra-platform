# Fase 5E — Frontend modular del Configuration Center

## Objetivo

Extraer la administración de configuraciones del `main.ts` heredado y exponerla como componente standalone bajo Angular Router.

## Ruta

- `/configuration`

## Archivos

- `frontend/src/app/configuration/configuration-center.service.ts`
- `frontend/src/app/configuration/configuration-center.component.ts`

## Funciones incluidas

- Consulta por ambiente.
- Creación y actualización de parámetros.
- Soporte para valores secretos.
- Validación regex.
- Exportación en formato `.env` y YAML.
- Validación de conectividad.
- Consulta de historial.
- Rollback manual por versión.

## Seguridad

- Las solicitudes utilizan el interceptor JWT común.
- Los secretos se muestran enmascarados por la API.
- La exportación no incluye secretos por defecto.
- El rollback requiere confirmación en la interfaz.

## Validaciones manuales pendientes

1. Compilar Angular.
2. Verificar permisos `CONFIG_READ` y `CONFIG_WRITE`.
3. Crear un parámetro no secreto en DEVELOPMENT.
4. Crear un secreto y confirmar que se muestra como `********`.
5. Exportar `.env` y YAML.
6. Ejecutar validación HTTP y TCP.
7. Modificar una clave y restaurar una versión anterior.
8. Confirmar que un usuario sin `CONFIG_WRITE` no puede guardar ni ejecutar rollback.

## Pendiente para retirar el frontend heredado

- Migrar Applications Center.
- Migrar Deployment Center.
- Cambiar el bootstrap definitivo a `AppComponent` y `appConfig`.
- Ejecutar pruebas de regresión del login, seguridad, configuración y monitoreo.
