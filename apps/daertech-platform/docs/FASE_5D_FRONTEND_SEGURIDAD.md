# Fase 5D — Frontend modular de seguridad

## Objetivo
Extraer Usuarios, Roles y Permisos del `main.ts` heredado hacia componentes standalone integrados al shell con Angular Router.

## Componentes
- `security-admin.service.ts`: cliente tipado para usuarios, roles y permisos.
- `users.component.ts`: alta, edición, eliminación, bloqueo, habilitación y asignación de roles.
- `roles.component.ts`: alta, edición, eliminación, activación y asignación de permisos.
- `permissions.component.ts`: catálogo de permisos por módulo.

## Rutas
- `/users`
- `/roles`
- `/permissions`

Todas las rutas se encuentran bajo `authGuard` y usan el interceptor JWT común.

## Corrección de integridad
Se agregaron endpoints de detalle:
- `GET /api/v1/admin/users/{id}`
- `GET /api/v1/admin/roles/{id}`

Estos endpoints devuelven `roleIds` y `permissionIds`. La interfaz consulta el detalle antes de editar para evitar reemplazar accidentalmente las asignaciones existentes con listas vacías.

## Validaciones manuales pendientes
1. Compilar Angular y verificar plantillas standalone.
2. Crear un usuario con uno o varios roles.
3. Editarlo sin cambiar roles y confirmar que las asignaciones se conservan.
4. Crear un rol con varios permisos.
5. Editarlo sin cambiar permisos y confirmar que las asignaciones se conservan.
6. Verificar respuestas 403 con usuarios sin privilegios.
7. Verificar restricciones de eliminación cuando existan relaciones dependientes.

## Siguiente extracción
Migrar Configuration Center, Applications Center y Deployment Center a rutas standalone. Después de validar esas pantallas podrá reemplazarse el bootstrap heredado del `main.ts`.
