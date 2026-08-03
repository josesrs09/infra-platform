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

INSERT INTO platform.permissions(id,code,name,module)
VALUES
(gen_random_uuid(),'CONFIG_APPLY','Aplicar configuraciones','CONFIGURATION'),
(gen_random_uuid(),'CONFIG_TEMPLATE','Administrar plantillas','CONFIGURATION')
ON CONFLICT (code) DO NOTHING;
