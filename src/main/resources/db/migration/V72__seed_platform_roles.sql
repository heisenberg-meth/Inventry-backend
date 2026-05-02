-- ============================================
-- V72__seed_platform_roles.sql  
-- Seed platform roles and associate with permissions
-- ============================================

-- 1. Check if ROOT role already exists (from V14 or previous tenant seeding)
-- If it doesn't exist with tenant_id IS NULL, create it
INSERT INTO roles (name, description, tenant_id, created_at)
SELECT 'ROOT', 'Global Platform Administrator', NULL, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ROOT' AND tenant_id IS NULL
);

-- 2. Check if PLATFORM_ADMIN role already exists
INSERT INTO roles (name, description, tenant_id, created_at)
SELECT 'PLATFORM_ADMIN', 'Platform Administrator', NULL, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'PLATFORM_ADMIN' AND tenant_id IS NULL
);

-- 3. Grant all permissions to ROOT role (if link doesn't exist)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROOT' AND r.tenant_id IS NULL
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- 4. Grant key permissions to PLATFORM_ADMIN (if link doesn't exist)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'PLATFORM_ADMIN' AND r.tenant_id IS NULL
AND p.key IN (
    'create_business', 'view_business', 'create_user', 'view_user',
    'update_user', 'view_product', 'create_product', 'update_product',
    'delete_product', 'view_reports', 'create_invoice', 'manage_platform'
)
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);