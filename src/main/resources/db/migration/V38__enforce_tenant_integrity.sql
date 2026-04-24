-- ============================================
-- V38__enforce_tenant_integrity.sql
-- Enforce database-level integrity for multi-tenancy
-- ============================================

-- 1. Ensure users.tenant_id is NOT NULL
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;

-- 2. Update users -> tenants FK with ON DELETE CASCADE
-- We try to drop known default names and our standardized name before re-adding
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_tenant_id_fkey;
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_tenant;

ALTER TABLE users 
ADD CONSTRAINT fk_users_tenant 
FOREIGN KEY (tenant_id) 
REFERENCES tenants(id) 
ON DELETE CASCADE;

-- 3. Update categories -> tenants FK with ON DELETE CASCADE
ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_tenant_id_fkey;
ALTER TABLE categories DROP CONSTRAINT IF EXISTS fk_categories_tenant;

ALTER TABLE categories 
ADD CONSTRAINT fk_categories_tenant 
FOREIGN KEY (tenant_id) 
REFERENCES tenants(id) 
ON DELETE CASCADE;
