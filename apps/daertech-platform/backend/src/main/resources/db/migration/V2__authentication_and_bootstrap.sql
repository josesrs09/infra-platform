CREATE TABLE platform.refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES platform.users(id) ON DELETE CASCADE,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_ip VARCHAR(64)
);
CREATE INDEX idx_refresh_tokens_user ON platform.refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expiry ON platform.refresh_tokens(expires_at);

INSERT INTO platform.permissions (id, code, name, module)
VALUES
  (gen_random_uuid(), 'USER_READ', 'Consultar usuarios', 'SECURITY'),
  (gen_random_uuid(), 'USER_WRITE', 'Administrar usuarios', 'SECURITY'),
  (gen_random_uuid(), 'ROLE_READ', 'Consultar roles', 'SECURITY'),
  (gen_random_uuid(), 'ROLE_WRITE', 'Administrar roles', 'SECURITY'),
  (gen_random_uuid(), 'AUDIT_READ', 'Consultar auditoría', 'AUDIT'),
  (gen_random_uuid(), 'CONFIG_READ', 'Consultar configuración', 'CONFIGURATION'),
  (gen_random_uuid(), 'CONFIG_WRITE', 'Administrar configuración', 'CONFIGURATION')
ON CONFLICT (code) DO NOTHING;

INSERT INTO platform.roles (id, code, name, description)
VALUES (gen_random_uuid(), 'ADMIN', 'Administrador', 'Acceso administrativo completo')
ON CONFLICT (code) DO NOTHING;

INSERT INTO platform.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM platform.roles r CROSS JOIN platform.permissions p
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
