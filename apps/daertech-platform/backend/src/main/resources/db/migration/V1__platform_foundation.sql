CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.roles (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(255),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE platform.permissions (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  module VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE platform.users (
  id UUID PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  email VARCHAR(180) NOT NULL UNIQUE,
  full_name VARCHAR(180) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  locked BOOLEAN NOT NULL DEFAULT FALSE,
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE platform.user_roles (
  user_id UUID NOT NULL REFERENCES platform.users(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES platform.roles(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE platform.role_permissions (
  role_id UUID NOT NULL REFERENCES platform.roles(id) ON DELETE CASCADE,
  permission_id UUID NOT NULL REFERENCES platform.permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE platform.configuration_items (
  id UUID PRIMARY KEY,
  category VARCHAR(80) NOT NULL,
  config_key VARCHAR(180) NOT NULL,
  config_value TEXT,
  secret BOOLEAN NOT NULL DEFAULT FALSE,
  environment VARCHAR(40) NOT NULL DEFAULT 'PRODUCTION',
  version BIGINT NOT NULL DEFAULT 1,
  updated_by UUID REFERENCES platform.users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (environment, config_key)
);

CREATE TABLE platform.configuration_history (
  id UUID PRIMARY KEY,
  configuration_id UUID NOT NULL REFERENCES platform.configuration_items(id) ON DELETE CASCADE,
  previous_value TEXT,
  new_value TEXT,
  reason VARCHAR(500),
  changed_by UUID REFERENCES platform.users(id),
  changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE platform.audit_events (
  id UUID PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actor_id UUID REFERENCES platform.users(id),
  action VARCHAR(120) NOT NULL,
  resource_type VARCHAR(120),
  resource_id VARCHAR(180),
  ip_address VARCHAR(64),
  correlation_id VARCHAR(120),
  success BOOLEAN NOT NULL,
  details JSONB
);

CREATE INDEX idx_audit_occurred_at ON platform.audit_events(occurred_at DESC);
CREATE INDEX idx_config_category ON platform.configuration_items(category, environment);
