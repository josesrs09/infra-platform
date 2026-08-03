# DAERTECH Platform — Documentación total para implementación

**Repositorio:** `josesrs09/infra-platform`  
**Ubicación del sistema:** `apps/daertech-platform/`  
**Rama documentada:** `feature/daertech-platform-foundation`  
**Estado:** versión candidata funcional validada por CI  
**Stack:** Spring Boot 3.5.3, Java 21, Angular 20, PostgreSQL 17, Redis 8 y Docker Compose.

> La habilitación en producción requiere completar endurecimiento, pruebas de restauración, pruebas operativas y aprobación formal de cambio.

## 1. Alcance

DAERTECH Platform centraliza:

- autenticación JWT, refresh tokens y cierre de sesión;
- usuarios, roles, permisos y RBAC;
- auditoría administrativa;
- Configuration Center;
- Applications Center;
- Deployment Center;
- Monitoring Center;
- frontend Angular modular;
- backend Spring Boot;
- persistencia PostgreSQL y Redis;
- observabilidad con Prometheus, Grafana, Loki y Alertmanager.

## 2. Arquitectura

```text
Internet
   |
DNS + Traefik + HTTPS
   |------------------------------|
Frontend Angular/Nginx        Backend Spring Boot
                                  |
                        PostgreSQL 17 + Redis 8
                                  |
               Prometheus + Grafana + Loki + Alertmanager
```

### Flujo de autenticación

1. El usuario envía credenciales a `POST /api/v1/auth/login`.
2. Spring Security valida usuario y contraseña BCrypt.
3. La API emite un access token JWT `HS256` y un refresh token opaco.
4. El frontend agrega `Authorization: Bearer <token>`.
5. El backend convierte `scope` en authorities y aplica RBAC.
6. El refresh token se almacena como hash SHA-256 y puede revocarse.

## 3. Requisitos

| Recurso | Mínimo | Recomendado |
|---|---:|---:|
| CPU | 4 vCPU | 8 vCPU |
| RAM | 8 GB | 16 GB |
| Disco | 120 GB SSD | 250 GB SSD |
| SO | Debian 12 / Ubuntu 24.04 | Debian 12 endurecido |
| Docker | 27+ | versión estable validada |
| Compose | v2 | versión estable validada |

## 4. Preparación del servidor

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y ca-certificates curl git gnupg ufw fail2ban jq openssl
sudo timedatectl set-timezone America/Santo_Domingo
sudo systemctl enable --now fail2ban

echo 'vm.overcommit_memory = 1' | sudo tee /etc/sysctl.d/99-daertech.conf
sudo sysctl --system
```

### Firewall

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from <IP_ADMINISTRATIVA>/32 to any port 22 proto tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

PostgreSQL, Redis, Prometheus, Loki y Alertmanager no deben exponerse directamente a Internet.

## 5. Clonado

```bash
sudo mkdir -p /opt/infra-platform
sudo chown "$USER":"$USER" /opt/infra-platform
git clone https://github.com/josesrs09/infra-platform.git /opt/infra-platform
cd /opt/infra-platform
git checkout feature/daertech-platform-foundation
cd apps/daertech-platform
cp .env.example .env
chmod 600 .env
```

Para producción debe usarse un tag o commit aprobado y registrar el SHA desplegado.

## 6. Variables obligatorias

| Variable | Requisito |
|---|---|
| `POSTGRES_PASSWORD` | fuerte y única |
| `REDIS_PASSWORD` | fuerte y única |
| `JWT_SECRET` | mínimo 64 caracteres |
| `CONFIG_ENCRYPTION_KEY` | mínimo 32 caracteres |
| `MONITORING_ALERTMANAGER_WEBHOOK_TOKEN` | aleatorio, 64+ caracteres |
| `ADMIN_PASSWORD` | mínimo 12 caracteres |
| `GRAFANA_ADMIN_PASSWORD` | fuerte y única |

```bash
openssl rand -base64 64
openssl rand -hex 32
openssl rand -base64 36
```

No almacenar secretos en Git, tickets o documentos. En producción debe emplearse un gestor de secretos.

## 7. Persistencia

| Ruta | Uso |
|---|---|
| `data/postgres` | datos PostgreSQL |
| `data/redis` | persistencia Redis/AOF |
| `generated` | configuraciones generadas |
| `backups/configuration` | respaldos de configuración |
| `managed-config` | configuraciones administradas |
| `workspaces` | workspaces de despliegue |
| `traefik/dynamic` | configuración dinámica |
| `secrets/ssh` | llaves de despliegue, solo lectura |

```bash
mkdir -p data/postgres data/redis generated backups/configuration \
  managed-config workspaces traefik/dynamic secrets/ssh
chmod 700 secrets/ssh
chmod 600 secrets/ssh/* 2>/dev/null || true
```

Debe estandarizarse si la persistencia quedará bajo el proyecto o bajo `/srv/daertech-platform`.

## 8. Construcción y arranque

```bash
docker compose config --quiet
docker compose -f docker-compose.monitoring.yml config --quiet

docker compose build --no-cache
docker compose up -d
docker compose ps
docker compose logs --tail=200 backend

docker compose -f docker-compose.monitoring.yml up -d
```

Orden interno:

1. PostgreSQL y Redis deben superar health checks.
2. Flyway aplica las migraciones.
3. Hibernate valida el esquema con `ddl-auto=validate`.
4. Spring Boot inicia en `8080` y contexto `/api/v1`.
5. Angular/Nginx inicia y consume `PUBLIC_API_URL`.

## 9. DNS, Traefik y HTTPS

Crear registros A:

```text
infra.daertechglobal.com
api-infra.daertechglobal.com
```

Rutas conceptuales:

```text
infra.daertechglobal.com     -> frontend:80
api-infra.daertechglobal.com -> backend:8080
```

Requisitos:

- redirección HTTP a HTTPS;
- certificados Let's Encrypt;
- HSTS y headers de seguridad;
- conservación de `X-Forwarded-For`, `X-Forwarded-Proto` y `X-Correlation-Id`;
- rate limiting en login;
- no publicar directamente `4200` ni `8080`.

## 10. Base de datos y Flyway

El esquema principal es `platform`. Migraciones actuales:

| Versión | Descripción |
|---|---|
| V1 | platform foundation |
| V2 | authentication and bootstrap |
| V3 | configuration center |
| V4 | configuration templates apply |
| V4.1 | service templates |
| V5 | applications center |
| V5.1 | configuration apply history |
| V6 | deployment center |
| V7 | deployment strategies and registry |
| V8 | deployment runtime operations |
| V9 | monitoring center |

Reglas:

- no modificar migraciones aplicadas;
- crear una nueva versión para cada cambio;
- no reutilizar números;
- respaldar antes de cambios destructivos;
- verificar `platform.flyway_schema_history`.

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT installed_rank,version,description,success FROM platform.flyway_schema_history ORDER BY installed_rank;"
```

## 11. Endpoints operativos

| Función | Endpoint |
|---|---|
| Health | `/api/v1/actuator/health` |
| Info | `/api/v1/actuator/info` |
| Métricas | `/api/v1/actuator/prometheus` |
| Login | `/api/v1/auth/login` |
| Usuarios | `/api/v1/admin/users` |

### Prueba de login

```bash
curl -sS -X POST https://api-infra.daertechglobal.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<CONTRASEÑA>"}' | jq
```

### Prueba protegida

```bash
TOKEN="<accessToken>"
curl -sS https://api-infra.daertechglobal.com/api/v1/admin/users \
  -H "Authorization: Bearer $TOKEN" | jq
```

Cambiar la contraseña inicial y crear cuentas nominativas.

## 12. Centros funcionales

### Configuration Center

- definir raíces permitidas;
- cifrar valores sensibles;
- validar antes de aplicar;
- respaldar antes de sobrescribir;
- registrar historial y actor;
- probar rollback.

### Applications Center

- registrar aplicación, propietario y repositorio;
- definir DEVELOPMENT, QA y PRODUCTION;
- separar variables de secretos;
- registrar versiones y dependencias;
- promover únicamente versiones validadas.

### Deployment Center

Mantener `DEPLOYMENT_EXECUTION_ENABLED=false` hasta probar en DEVELOPMENT/QA. El montaje de `/var/run/docker.sock` equivale a control administrativo del host; la arquitectura objetivo debe usar un agente dedicado con mínimo privilegio.

### Monitoring Center

Validar:

- Prometheus targets UP;
- datasources y dashboards Grafana;
- Loki y recolección de logs;
- Alertmanager y webhook;
- Node Exporter;
- cAdvisor.

## 13. Backups

```bash
mkdir -p /srv/daertech-platform/exports

docker compose exec -T postgres pg_dump \
  -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" \
  > /srv/daertech-platform/exports/platform-$(date +%F-%H%M).dump

sha256sum /srv/daertech-platform/exports/platform-*.dump
```

Configuraciones:

```bash
tar -C /opt/infra-platform/apps/daertech-platform \
  -czf /srv/daertech-platform/exports/config-$(date +%F-%H%M).tar.gz \
  managed-config generated backups/configuration traefik/dynamic
```

Un backup no se considera válido hasta completar una restauración y prueba funcional.

## 14. Actualización y rollback

1. Registrar commit actual y nuevo.
2. Confirmar CI en verde.
3. Crear backup.
4. Revisar migraciones.
5. Desplegar tag aprobado.
6. Validar health, login, endpoint protegido y frontend.
7. Mantener versión anterior durante la ventana de reversión.

```bash
git rev-parse HEAD
git fetch --tags
git checkout <TAG_APROBADO>
docker compose build
docker compose up -d
```

No revertir una migración destructiva sin un plan específico de datos.

## 15. CI y criterio de cierre

El workflow propio valida:

- Java 21 y `mvn verify`;
- versiones Flyway únicas;
- Angular 20 con Node.js 22;
- Compose principal y de monitoreo;
- PostgreSQL 17 y Redis 8 limpios;
- aplicación saludable;
- login del administrador;
- emisión JWT;
- acceso autenticado a usuarios.

La versión candidata funcional alcanzó este criterio.

## 16. Acciones obligatorias antes del release

- unificar versión Maven `0.2.0` y `info.app.version` `0.6.1`;
- restringir puertos internos;
- estandarizar rutas persistentes;
- separar acceso al Docker socket;
- ejecutar pruebas de carga;
- ejecutar backup y restauración;
- completar prueba de seguridad y continuidad;
- crear tag inmutable.

## 17. Checklist DEVELOPMENT/QA

- [ ] Servidor preparado y actualizado.
- [ ] Secretos no productivos configurados.
- [ ] Compose validado.
- [ ] PostgreSQL y Redis healthy.
- [ ] 11 migraciones aplicadas.
- [ ] Health UP.
- [ ] Login y JWT correctos.
- [ ] Usuarios, roles y permisos probados.
- [ ] Frontend accesible.
- [ ] Centros funcionales probados.
- [ ] Monitoreo operativo.
- [ ] Backup y restauración probados.

## 18. Checklist producción

- [ ] Aprobación formal de arquitectura y seguridad.
- [ ] Tag y versión unificada.
- [ ] SSH endurecido y firewall activo.
- [ ] DNS y HTTPS válidos.
- [ ] PostgreSQL y Redis no expuestos.
- [ ] Actuator y monitoreo protegidos.
- [ ] Gestor de secretos habilitado.
- [ ] Cuentas nominativas y contraseña inicial cambiada.
- [ ] Backups automáticos y restauración probada.
- [ ] Retención de logs y métricas definida.
- [ ] Pruebas de carga, seguridad y rollback completadas.
- [ ] Plan de continuidad aprobado.
