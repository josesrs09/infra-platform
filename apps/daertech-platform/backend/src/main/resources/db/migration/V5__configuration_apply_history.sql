CREATE TABLE platform.configuration_apply_history (
  id UUID PRIMARY KEY,
  template_code VARCHAR(80),
  environment VARCHAR(40) NOT NULL,
  target_path VARCHAR(500) NOT NULL,
  backup_path VARCHAR(500),
  service_name VARCHAR(120),
  action_name VARCHAR(40) NOT NULL,
  result_status VARCHAR(40) NOT NULL,
  checksum_before VARCHAR(64),
  checksum_after VARCHAR(64),
  requested_by VARCHAR(80) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  health_url VARCHAR(500),
  health_status INTEGER,
  details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ
);

CREATE INDEX idx_configuration_apply_created ON platform.configuration_apply_history(created_at DESC);
CREATE INDEX idx_configuration_apply_target ON platform.configuration_apply_history(target_path, created_at DESC);
