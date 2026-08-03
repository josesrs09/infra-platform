# Fase 4D — Interfaz del Deployment Center

## Alcance

La interfaz Angular permite administrar el ciclo operativo de despliegues desde el panel de DAERTECH Platform.

## Funciones

- Consultar despliegues por ambiente.
- Crear solicitudes por aplicación, versión, rama y motivo.
- Consultar el detalle completo del despliegue.
- Visualizar pasos, códigos de salida y logs.
- Ejecutar solicitudes pendientes o fallidas.
- Solicitar rollback.
- Configurar estrategia RECREATE, ROLLING o BLUE_GREEN.
- Promover una versión exitosa a otro ambiente.
- Publicar la imagen en el registro privado.
- Conmutar tráfico al slot BLUE o GREEN.
- Consultar eventos runtime.

## Controles

Las acciones se mantienen protegidas por los permisos del backend. Ocultar un botón en frontend no sustituye la autorización mediante `@PreAuthorize`.

La ejecución real continúa condicionada por:

```env
DEPLOYMENT_EXECUTION_ENABLED=false
```

Debe mantenerse desactivada hasta completar las pruebas en DEVELOPMENT o QA.

## Pruebas manuales mínimas

1. Registrar una aplicación con repositorio, Dockerfile y health endpoint válidos.
2. Crear una solicitud en DEVELOPMENT.
3. Confirmar que aparece como PENDING.
4. Ejecutar la solicitud con la ejecución real deshabilitada y validar el mensaje de bloqueo.
5. Habilitar temporalmente la ejecución en un servidor de pruebas.
6. Validar checkout, build, Compose y health check.
7. Consultar los pasos y logs desde la interfaz.
8. Configurar registry e imagen y probar publicación.
9. Para BLUE_GREEN, confirmar ambos servicios antes de conmutar tráfico.
10. Probar rollback y promoción hacia QA.

## Pendientes antes de producción

- Sustituir el componente Angular monolítico por módulos y servicios especializados.
- Incorporar confirmaciones explícitas para execute, rollback, push y traffic switch.
- Implementar polling o eventos server-sent para operaciones largas.
- Limitar y paginar logs extensos.
- Ejecutar pruebas de componentes y pruebas end-to-end.
