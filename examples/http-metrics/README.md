# Instrumentación HTTP por aplicación

Todas las aplicaciones deben publicar `/metrics` dentro de `infra_monitoring` y usar estas métricas:

- `http_requests_total{application,environment,method,route,status_code}`
- `http_request_duration_seconds_bucket{application,environment,method,route,status_code,le}`
- `http_request_duration_seconds_sum`
- `http_request_duration_seconds_count`

No use URL completas con identificadores como etiqueta. Normalice `/clientes/123` a `/clientes/{id}` para evitar cardinalidad excesiva.

## Node.js / Express

Ejemplo ejecutable en `node-express/`.

```bash
cd examples/http-metrics/node-express
npm install
APPLICATION_NAME=api-facturacion ENVIRONMENT=production PORT=3000 npm start
```

## Go

Ejemplo ejecutable en `go/`.

```bash
cd examples/http-metrics/go
APPLICATION_NAME=api-recaudo ENVIRONMENT=production PORT=8080 go run .
```

## Spring Boot

Dependencias Maven:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

`application.yml`:

```yaml
spring:
  application:
    name: api-banca
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${APP_ENVIRONMENT:development}
```

Prometheus debe consultar `/actuator/prometheus`. Para mantener el contrato común, configure un relabel o exponga `/metrics` mediante Spring Security/proxy.

## PHP

Instale:

```bash
composer require promphp/prometheus_client_php
```

Middleware conceptual:

```php
<?php
use Prometheus\CollectorRegistry;
use Prometheus\Storage\InMemory;

$registry = new CollectorRegistry(new InMemory());
$counter = $registry->getOrRegisterCounter(
    'app', 'http_requests_total', 'Total HTTP requests',
    ['application', 'environment', 'method', 'route', 'status_code']
);
$histogram = $registry->getOrRegisterHistogram(
    'app', 'http_request_duration_seconds', 'HTTP duration',
    ['application', 'environment', 'method', 'route', 'status_code'],
    [0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2, 5]
);
```

En producción use almacenamiento APCu o Redis, no `InMemory`, cuando existan múltiples workers.

## Registro en Prometheus

Agregue cada aplicación en `monitoring/prometheus/targets/applications.yml`:

```yaml
- targets:
    - api-facturacion:3000
  labels:
    application: api-facturacion
    environment: production
```

La aplicación debe estar conectada a `infra_monitoring` y resolver por el nombre configurado en `targets`.
