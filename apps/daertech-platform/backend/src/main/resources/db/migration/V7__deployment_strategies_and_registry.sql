ALTER TABLE platform.deployments
  ADD COLUMN IF NOT EXISTS strategy VARCHAR(30) NOT NULL DEFAULT 'RECREATE',
  ADD COLUMN IF NOT EXISTS registry_id UUID,
  ADD COLUMN IF NOT EXISTS registry_image VARCHAR(300),
  ADD COLUMN IF NOT EXISTS promoted_from UUID REFERENCES platform.deployments(id),
  ADD COLUMN IF NOT EXISTS notification_status VARCHAR(30);

CREATE TABLE IF NOT EXISTS platform.container_registries (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  registry_url VARCHAR(300) NOT NULL,
  username_secret_key VARCHAR(180),
  password_secret_key VARCHAR(180),
  insecure BOOLEAN NOT NULL DEFAULT FALSE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE platform.deployments
  ADD CONSTRAINT fk_deployment_registry FOREIGN KEY (registry_id)
  REFERENCES platform.container_registries(id);

CREATE INDEX IF NOT EXISTS idx_deployments_strategy ON platform.deployments(strategy);
CREATE INDEX IF NOT EXISTS idx_deployments_registry ON platform.deployments(registry_id);

INSERT INTO platform.permissions(id,code,name,module)
SELECT gen_random_uuid(),'REGISTRY_READ','Consultar registros de imágenes','DEPLOYMENT'
WHERE NOT EXISTS (SELECT 1 FROM platform.permissions WHERE code='REGISTRY_READ');
INSERT INTO platform.permissions(id,code,name,module)
SELECT gen_random_uuid(),'REGISTRY_WRITE','Administrar registros de imágenes','DEPLOYMENT'
WHERE NOT EXISTS (SELECT 1 FROM platform.permissions WHERE code='REGISTRY_WRITE');

INSERT INTO platform.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM platform.roles r CROSS JOIN platform.permissions p
WHERE r.code='ADMIN' AND p.code IN ('REGISTRY_READ','REGISTRY_WRITE')
ON CONFLICT DO NOTHING;
