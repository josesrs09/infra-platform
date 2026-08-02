# DAERTECH Platform

Plataforma central de administración de infraestructura, aplicaciones, configuraciones, despliegues, monitoreo, auditoría y respaldos de DAERTECH.

## Componentes

- `backend/`: API Spring Boot.
- `frontend/`: aplicación Angular.
- `docker-compose.yml`: entorno local y base de producción.
- `.env.example`: variables requeridas sin secretos.
- `docs/PUESTA_EN_MARCHA.md`: pasos manuales para instalar y operar.

## Arranque rápido

```bash
cp .env.example .env
docker compose up -d --build
```

Frontend: `http://localhost:4200`

Backend: `http://localhost:8080/api/v1`

Actuator: `http://localhost:8080/actuator/health`

## Seguridad

Nunca confirmes secretos dentro del repositorio. Configura contraseñas, tokens, certificados y credenciales únicamente en `.env`, Docker secrets o el gestor de secretos del entorno.
