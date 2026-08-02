CREATE TABLE platform.service_templates (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(140) NOT NULL,
  service_type VARCHAR(80) NOT NULL,
  description VARCHAR(500),
  schema_json JSONB NOT NULL,
  template_text TEXT NOT NULL,
  output_format VARCHAR(20) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO platform.service_templates(id,code,name,service_type,description,schema_json,template_text,output_format)
VALUES
(gen_random_uuid(),'POSTGRESQL_ENV','PostgreSQL','POSTGRESQL','Variables de conexión PostgreSQL','{"required":["HOST","PORT","DATABASE","USERNAME","PASSWORD"]}','POSTGRES_HOST={{HOST}}\nPOSTGRES_PORT={{PORT}}\nPOSTGRES_DB={{DATABASE}}\nPOSTGRES_USER={{USERNAME}}\nPOSTGRES_PASSWORD={{PASSWORD}}','ENV'),
(gen_random_uuid(),'REDIS_ENV','Redis','REDIS','Variables de conexión Redis','{"required":["HOST","PORT","PASSWORD"]}','REDIS_HOST={{HOST}}\nREDIS_PORT={{PORT}}\nREDIS_PASSWORD={{PASSWORD}}','ENV'),
(gen_random_uuid(),'RABBITMQ_ENV','RabbitMQ','RABBITMQ','Variables de conexión RabbitMQ','{"required":["HOST","PORT","USERNAME","PASSWORD","VHOST"]}','RABBITMQ_HOST={{HOST}}\nRABBITMQ_PORT={{PORT}}\nRABBITMQ_USERNAME={{USERNAME}}\nRABBITMQ_PASSWORD={{PASSWORD}}\nRABBITMQ_VHOST={{VHOST}}','ENV'),
(gen_random_uuid(),'MQTT_ENV','MQTT','MQTT','Variables de conexión MQTT','{"required":["HOST","PORT","USERNAME","PASSWORD","CLIENT_ID"]}','MQTT_HOST={{HOST}}\nMQTT_PORT={{PORT}}\nMQTT_USERNAME={{USERNAME}}\nMQTT_PASSWORD={{PASSWORD}}\nMQTT_CLIENT_ID={{CLIENT_ID}}','ENV'),
(gen_random_uuid(),'SMTP_ENV','SMTP','SMTP','Variables de conexión SMTP','{"required":["HOST","PORT","USERNAME","PASSWORD","FROM"]}','SMTP_HOST={{HOST}}\nSMTP_PORT={{PORT}}\nSMTP_USERNAME={{USERNAME}}\nSMTP_PASSWORD={{PASSWORD}}\nSMTP_FROM={{FROM}}','ENV'),
(gen_random_uuid(),'MINIO_ENV','MinIO','MINIO','Variables de conexión MinIO','{"required":["ENDPOINT","ACCESS_KEY","SECRET_KEY","BUCKET"]}','MINIO_ENDPOINT={{ENDPOINT}}\nMINIO_ACCESS_KEY={{ACCESS_KEY}}\nMINIO_SECRET_KEY={{SECRET_KEY}}\nMINIO_BUCKET={{BUCKET}}','ENV'),
(gen_random_uuid(),'TRAEFIK_DYNAMIC_YAML','Traefik Dynamic','TRAEFIK','Router y servicio HTTP de Traefik','{"required":["ROUTER","HOST","SERVICE","URL"]}','http:\n  routers:\n    {{ROUTER}}:\n      rule: "Host(`{{HOST}}`)"\n      service: {{SERVICE}}\n      tls:\n        certResolver: letsencrypt\n  services:\n    {{SERVICE}}:\n      loadBalancer:\n        servers:\n          - url: "{{URL}}"','YAML'),
(gen_random_uuid(),'PROMETHEUS_TARGET_YAML','Prometheus Target','PROMETHEUS','Job de scraping Prometheus','{"required":["JOB_NAME","TARGET","METRICS_PATH"]}','- job_name: "{{JOB_NAME}}"\n  metrics_path: "{{METRICS_PATH}}"\n  static_configs:\n    - targets: ["{{TARGET}}"]','YAML');
