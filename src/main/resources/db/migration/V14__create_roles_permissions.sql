-- ============================================
-- V14__create_roles_permissions.sql
-- Roles and permissions system
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'roles') THEN
        CREATE TABLE roles (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR(100)   NOT NULL,
  description     TEXT,
  tenant_id       BIGINT         REFERENCES tenants(id),
  created_at      TIMESTAMP      DEFAULT NOW()
);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_roles_tenant') THEN
        CREATE INDEX idx_roles_tenant ON roles(tenant_id);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'permissions') THEN
        CREATE TABLE permissions (
  id              BIGSERIAL PRIMARY KEY,
  "key"           VARCHAR(100)   UNIQUE NOT NULL,
  description     VARCHAR(255)   NOT NULL
);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'role_permissions') THEN
        CREATE TABLE role_permissions (
  role_id         BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id   BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);
    END IF;
END $$;

-- Seed default permissions
INSERT INTO permissions ("key", description) VALUES
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
  ('manage_platform', 'Full platform administrative control');
