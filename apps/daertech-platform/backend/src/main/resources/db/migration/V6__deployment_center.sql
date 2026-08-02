CREATE TABLE platform.deployments (
  id UUID PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES platform.applications(id) ON DELETE CASCADE,
  environment VARCHAR(40) NOT NULL,
  version VARCHAR(120) NOT NULL,
  git_branch VARCHAR(180),
  git_commit VARCHAR(64),
  image_tag VARCHAR(255),
  status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  previous_deployment_id UUID REFERENCES platform.deployments(id),
  requested_by VARCHAR(120) NOT NULL,
  reason VARCHAR(500),
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  health_status VARCHAR(40),
  health_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE platform.deployment_steps (
  id UUID PRIMARY KEY,
  deployment_id UUID NOT NULL REFERENCES platform.deployments(id) ON DELETE CASCADE,
  step_order INTEGER NOT NULL,
  step_name VARCHAR(120) NOT NULL,
  status VARCHAR(40) NOT NULL,
  command TEXT,
  output TEXT,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  exit_code INTEGER,
  UNIQUE(deployment_id, step_order)
);

CREATE TABLE platform.deployment_artifacts (
  id UUID PRIMARY KEY,
  deployment_id UUID NOT NULL REFERENCES platform.deployments(id) ON DELETE CASCADE,
  artifact_type VARCHAR(60) NOT NULL,
  artifact_name VARCHAR(255) NOT NULL,
  artifact_reference TEXT NOT NULL,
  checksum_sha256 VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deployments_app_env_created ON platform.deployments(application_id, environment, created_at DESC);
CREATE INDEX idx_deployment_steps_deployment ON platform.deployment_steps(deployment_id, step_order);

INSERT INTO platform.permissions(id, code, name, module)
VALUES
  (gen_random_uuid(), 'DEPLOYMENT_READ', 'Consultar despliegues', 'DEPLOYMENT'),
  (gen_random_uuid(), 'DEPLOYMENT_EXECUTE', 'Ejecutar despliegues', 'DEPLOYMENT'),
  (gen_random_uuid(), 'DEPLOYMENT_ROLLBACK', 'Revertir despliegues', 'DEPLOYMENT')
ON CONFLICT (code) DO NOTHING;

INSERT INTO platform.role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM platform.roles r CROSS JOIN platform.permissions p
WHERE r.code='ADMIN' AND p.code IN ('DEPLOYMENT_READ','DEPLOYMENT_EXECUTE','DEPLOYMENT_ROLLBACK')
ON CONFLICT DO NOTHING;
