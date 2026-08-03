# Fase 5C — Refactor Angular y Monitoring Center

## Objetivo

Reducir la responsabilidad del `main.ts` monolítico y preparar la integración mantenible del Monitoring Center.

## Estructura agregada

```text
frontend/src/app/
├── app.config.ts
├── core/
│   ├── api-client.service.ts
│   ├── auth-session.service.ts
│   └── auth.interceptor.ts
└── monitoring/
    ├── monitoring.service.ts
    └── monitoring-center.component.ts
```

## Responsabilidades

- `AuthSessionService`: administra access token, refresh token y cierre local de sesión.
- `authInterceptor`: adjunta Bearer token y limpia la sesión ante respuestas HTTP 401.
- `ApiClientService`: centraliza la URL `/api/v1`, parámetros y operaciones HTTP comunes.
- `MonitoringService`: expone dashboard, targets, guardado y health checks con interfaces tipadas.
- `MonitoringCenterComponent`: pantalla standalone para indicadores, filtros, alta de objetivos y validación manual.
- `appConfig`: registra `HttpClient` y el interceptor funcional.

## Integración pendiente

El `main.ts` existente conserva actualmente las pantallas productivas de seguridad, configuración, aplicaciones y despliegues. Para evitar una regresión no se reemplazó de forma abrupta.

La siguiente iteración debe:

1. Crear `AppComponent` como shell de navegación.
2. Extraer login y módulos actuales a componentes standalone.
3. Introducir Angular Router con rutas protegidas.
4. Cambiar el bootstrap a `bootstrapApplication(AppComponent, appConfig)`.
5. Registrar la ruta `/monitoring` con `MonitoringCenterComponent`.
6. Eliminar headers Bearer construidos manualmente una vez activado el interceptor.
7. Ejecutar `npm ci` y `npm run build` antes de retirar el `main.ts` anterior.

## Criterios de aceptación

- La aplicación compila sin errores TypeScript.
- El login conserva access y refresh token.
- Las respuestas 401 cierran la sesión local.
- Monitoring Center lista objetivos y dashboard.
- El alta y la validación manual funcionan con permisos RBAC.
- Las pantallas anteriores conservan su comportamiento.

## Riesgo controlado

La integración final se mantiene pendiente hasta ejecutar una compilación real, porque sustituir el archivo monolítico sin validación podría romper funcionalidades ya incorporadas.
