ALTER TABLE platform.container_registries
  ADD COLUMN IF NOT EXISTS push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS verify_tls BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE platform.deployments
  ADD COLUMN IF NOT EXISTS active_slot VARCHAR(10),
  ADD COLUMN IF NOT EXISTS registry_pushed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS traffic_switched_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS platform.deployment_runtime_events (
  id UUID PRIMARY KEY,
  deployment_id UUID NOT NULL REFERENCES platform.deployments(id) ON DELETE CASCADE,
  event_type VARCHAR(50) NOT NULL,
  status VARCHAR(30) NOT NULL,
  details TEXT,
  performed_by VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_deployment_runtime_events_deployment
  ON platform.deployment_runtime_events(deployment_id, created_at DESC);

INSERT INTO platform.permissions(id,code,name,module)
SELECT gen_random_uuid(),'DEPLOYMENT_TRAFFIC_SWITCH','Conmutar tráfico blue/green','DEPLOYMENT'
WHERE NOT EXISTS (SELECT 1 FROM platform.permissions WHERE code='DEPLOYMENT_TRAFFIC_SWITCH');

INSERT INTO platform.permissions(id,code,name,module)
SELECT gen_random_uuid(),'DEPLOYMENT_REGISTRY_PUSH','Publicar imágenes en registro','DEPLOYMENT'
WHERE NOT EXISTS (SELECT 1 FROM platform.permissions WHERE code='DEPLOYMENT_REGISTRY_PUSH');

INSERT INTO platform.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM platform.roles r CROSS JOIN platform.permissions p
WHERE r.code='ADMIN' AND p.code IN ('DEPLOYMENT_TRAFFIC_SWITCH','DEPLOYMENT_REGISTRY_PUSH')
ON CONFLICT DO NOTHING;