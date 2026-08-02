CREATE TABLE platform.applications (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(500),
  repository_url VARCHAR(500) NOT NULL,
  default_branch VARCHAR(120) NOT NULL DEFAULT 'main',
  technology VARCHAR(80) NOT NULL,
  build_tool VARCHAR(80),
  dockerfile_path VARCHAR(255) NOT NULL DEFAULT 'Dockerfile',
  context_path VARCHAR(255) NOT NULL DEFAULT '.',
  internal_port INTEGER,
  health_path VARCHAR(255),
  metrics_path VARCHAR(255),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE platform.application_environments (
  id UUID PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES platform.applications(id) ON DELETE CASCADE,
  environment VARCHAR(40) NOT NULL,
  branch VARCHAR(120),
  public_url VARCHAR(500),
  replicas INTEGER NOT NULL DEFAULT 1,
  cpu_limit VARCHAR(40),
  memory_limit VARCHAR(40),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE(application_id, environment)
);

CREATE TABLE platform.application_variables (
  id UUID PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES platform.applications(id) ON DELETE CASCADE,
  environment VARCHAR(40) NOT NULL,
  variable_key VARCHAR(180) NOT NULL,
  variable_value TEXT,
  secret BOOLEAN NOT NULL DEFAULT FALSE,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  description VARCHAR(500),
  UNIQUE(application_id, environment, variable_key)
);

CREATE TABLE platform.application_dependencies (
  id UUID PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES platform.applications(id) ON DELETE CASCADE,
  dependency_type VARCHAR(80) NOT NULL,
  dependency_name VARCHAR(160) NOT NULL,
  target VARCHAR(500),
  required BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE platform.application_versions (
  id UUID PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES platform.applications(id) ON DELETE CASCADE,
  version VARCHAR(120) NOT NULL,
  git_commit VARCHAR(80),
  image_tag VARCHAR(255),
  notes TEXT,
  created_by VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(application_id, version)
);

INSERT INTO platform.permissions(id,code,name,module)
VALUES
  (gen_random_uuid(),'APPLICATION_READ','Consultar aplicaciones','APPLICATIONS'),
  (gen_random_uuid(),'APPLICATION_WRITE','Administrar aplicaciones','APPLICATIONS')
ON CONFLICT (code) DO NOTHING;

INSERT INTO platform.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM platform.roles r CROSS JOIN platform.permissions p
WHERE r.code='ADMIN' AND p.code IN ('APPLICATION_READ','APPLICATION_WRITE')
ON CONFLICT DO NOTHING;

CREATE INDEX idx_applications_active ON platform.applications(active,name);
CREATE INDEX idx_app_versions_created ON platform.application_versions(application_id,created_at DESC);
