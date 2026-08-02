CREATE TABLE platform.configuration_templates (
  id UUID PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  service_type VARCHAR(80) NOT NULL,
  format VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE platform.configuration_profiles (
  id UUID PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  environment VARCHAR(40) NOT NULL,
  template_id UUID REFERENCES platform.configuration_templates(id),
  target_path VARCHAR(500) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE platform.configuration_apply_history (
  id UUID PRIMARY KEY,
  profile_id UUID REFERENCES platform.configuration_profiles(id),
  environment VARCHAR(40) NOT NULL,
  target_path VARCHAR(500) NOT NULL,
  backup_path VARCHAR(700),
  checksum_before VARCHAR(64),
  checksum_after VARCHAR(64),
  status VARCHAR(30) NOT NULL,
  reason VARCHAR(500),
  applied_by VARCHAR(120),
  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  details JSONB
);

CREATE INDEX idx_cfg_apply_history_date ON platform.configuration_apply_history(applied_at DESC);

INSERT INTO platform.permissions(id,code,name,module)
VALUES
(gen_random_uuid(),'CONFIG_APPLY','Aplicar configuraciones','CONFIGURATION'),
(gen_random_uuid(),'CONFIG_TEMPLATE','Administrar plantillas','CONFIGURATION')
ON CONFLICT (code) DO NOTHING;
