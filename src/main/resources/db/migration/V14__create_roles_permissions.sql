-- ============================================
-- V14__create_roles_permissions.sql
-- Roles and permissions system
-- ============================================

CREATE TABLE roles (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR(100)   NOT NULL,
  description     TEXT,
  tenant_id       BIGINT         REFERENCES tenants(id),
  created_at      TIMESTAMP      DEFAULT NOW()
);

CREATE INDEX idx_roles_tenant ON roles(tenant_id);

CREATE TABLE permissions (
  id              BIGSERIAL PRIMARY KEY,
  key             VARCHAR(100)   UNIQUE NOT NULL,
  description     VARCHAR(255)   NOT NULL
);

CREATE TABLE role_permissions (
  role_id         BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id   BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

-- Seed default permissions
INSERT INTO permissions (key, description) VALUES
  ('create_business', 'Can create businesses'),
  ('view_business', 'Can view business details'),
  ('create_user', 'Can create and manage users'),
  ('view_user', 'Can view user details'),
  ('update_user', 'Can update user details'),
  ('delete_user', 'Can deactivate users'),
  ('create_product', 'Can create products'),
  ('view_product', 'Can view products'),
  ('update_product', 'Can update products'),
  ('delete_product', 'Can delete products'),
  ('stock_in', 'Can record stock in movements'),
  ('stock_out', 'Can record stock out movements'),
  ('view_reports', 'Can view business reports'),
  ('create_invoice', 'Can create invoices'),
  ('manage_platform', 'Full platform administrative control')
ON CONFLICT (key) DO NOTHING;
