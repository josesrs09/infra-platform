CREATE TABLE IF NOT EXISTS platform.monitoring_targets (
  id UUID PRIMARY KEY,
  application_id UUID REFERENCES platform.applications(id) ON DELETE CASCADE,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(180) NOT NULL,
  environment VARCHAR(40) NOT NULL,
  target_type VARCHAR(40) NOT NULL DEFAULT 'HTTP',
  health_url VARCHAR(500),
  metrics_url VARCHAR(500),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  timeout_ms INTEGER NOT NULL DEFAULT 5000,
  check_interval_seconds INTEGER NOT NULL DEFAULT 60,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS platform.monitoring_checks (
  id UUID PRIMARY KEY,
  target_id UUID NOT NULL REFERENCES platform.monitoring_targets(id) ON DELETE CASCADE,
  status VARCHAR(30) NOT NULL,
  http_status INTEGER,
  response_time_ms BIGINT,
  message VARCHAR(2000),
  checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_monitoring_targets_environment ON platform.monitoring_targets(environment,enabled);
CREATE INDEX IF NOT EXISTS idx_monitoring_checks_target_time ON platform.monitoring_checks(target_id,checked_at DESC);

INSERT INTO platform.permissions(id,code,name,module)
SELECT gen_random_uuid(),'MONITORING_READ','Consultar monitoreo y estado de servicios','MONITORING'
WHERE NOT EXISTS (SELECT 1 FROM platform.permissions WHERE code='MONITORING_READ');
INSERT INTO platform.permissions(id,code,name,module)
SELECT gen_random_uuid(),'MONITORING_WRITE','Administrar objetivos de monitoreo','MONITORING'
WHERE NOT EXISTS (SELECT 1 FROM platform.permissions WHERE code='MONITORING_WRITE');
INSERT INTO platform.permissions(id,code,name,module)
SELECT gen_random_uuid(),'MONITORING_EXECUTE','Ejecutar verificaciones de monitoreo','MONITORING'
WHERE NOT EXISTS (SELECT 1 FROM platform.permissions WHERE code='MONITORING_EXECUTE');

INSERT INTO platform.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM platform.roles r CROSS JOIN platform.permissions p
WHERE r.code='ADMIN' AND p.code IN ('MONITORING_READ','MONITORING_WRITE','MONITORING_EXECUTE')
ON CONFLICT DO NOTHING;