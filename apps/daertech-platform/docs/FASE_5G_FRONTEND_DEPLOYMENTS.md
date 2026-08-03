# Fase 5G — Frontend modular del Deployment Center

## Alcance

Se extrajo la administración de despliegues del `main.ts` heredado hacia componentes y servicios standalone.

## Ruta

```text
/deployments
```

## Funciones

- Listado y filtro por ambiente.
- Creación de solicitudes de despliegue.
- Consulta de detalle, pasos, salida y códigos de retorno.
- Ejecución controlada.
- Solicitud de rollback con motivo.
- Selección de estrategia `RECREATE`, `ROLLING` o `BLUE_GREEN`.
- Asociación con registry e imagen destino.
- Promoción entre ambientes.
- Publicación de imagen.
- Conmutación de tráfico `BLUE`/`GREEN`.
- Consulta de eventos runtime.

## Seguridad

La interfaz depende de los permisos backend existentes:

- `DEPLOYMENT_READ`
- `DEPLOYMENT_EXECUTE`
- `DEPLOYMENT_ROLLBACK`
- `DEPLOYMENT_REGISTRY_PUSH`
- `DEPLOYMENT_TRAFFIC_SWITCH`
- `REGISTRY_READ`

La ejecución real permanece gobernada por `APP_DEPLOYMENT_EXECUTION_ENABLED`.

## Pruebas manuales

1. Crear una solicitud en DEVELOPMENT.
2. Asignar estrategia y registry.
3. Confirmar que el detalle muestra pasos y eventos.
4. Probar ejecución con la ejecución real deshabilitada y validar el mensaje de seguridad.
5. Probar promoción desde un deployment exitoso.
6. Publicar una imagen usando secretos válidos del Configuration Center.
7. Conmutar a GREEN y regresar a BLUE.
8. Solicitar rollback y verificar la nueva solicitud generada.

## Pendientes

- Compilar Angular y corregir incompatibilidades de tipos o plantilla.
- Sustituir el bootstrap heredado por `AppComponent` y `appConfig`.
- Retirar la ruta temporal `/legacy` después de validar paridad funcional.
- Separar la ejecución con acceso al socket Docker en un agente dedicado.
