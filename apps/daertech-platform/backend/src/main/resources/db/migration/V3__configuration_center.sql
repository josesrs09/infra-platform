ALTER TABLE platform.configuration_items
  ADD COLUMN IF NOT EXISTS value_type VARCHAR(30) NOT NULL DEFAULT 'STRING',
  ADD COLUMN IF NOT EXISTS description VARCHAR(500),
  ADD COLUMN IF NOT EXISTS validation_rule VARCHAR(500),
  ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE platform.configuration_history
  ADD COLUMN IF NOT EXISTS operation VARCHAR(30) NOT NULL DEFAULT 'UPDATE',
  ADD COLUMN IF NOT EXISTS version BIGINT,
  ADD COLUMN IF NOT EXISTS success BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS platform.configuration_environments (
  id UUID PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(300),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO platform.configuration_environments(id,code,name,description)
VALUES
  ('00000000-0000-0000-0000-000000000101','DEVELOPMENT','Desarrollo','Entorno local y de desarrollo'),
  ('00000000-0000-0000-0000-000000000102','QA','QA','Pruebas funcionales e integración'),
  ('00000000-0000-0000-0000-000000000103','CERTIFICATION','Certificación','Certificación con terceros'),
  ('00000000-0000-0000-0000-000000000104','PRODUCTION','Producción','Entorno productivo')
ON CONFLICT (code) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_config_key_environment ON platform.configuration_items(config_key, environment);
CREATE INDEX IF NOT EXISTS idx_config_history_config_changed ON platform.configuration_history(configuration_id, changed_at DESC);
