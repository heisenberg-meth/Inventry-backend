-- ============================================
-- V52__convert_user_role_to_relation.sql
-- Converting string-based user role to a proper relational ManyToOne
-- ============================================

-- 1. Add the role_id column
ALTER TABLE users ADD COLUMN role_id BIGINT;

-- 2. Ensure default roles exist for all active role strings in the system
-- This handles both tenant-specific roles and platform-level roles (where tenant_id is NULL)
INSERT INTO roles (name, tenant_id, description)
SELECT DISTINCT u.role, u.tenant_id, 'Default ' || u.role || ' role'
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM roles r
    WHERE r.name = u.role
    AND (r.tenant_id = u.tenant_id OR (r.tenant_id IS NULL AND u.tenant_id IS NULL))
);

-- 3. Map the role_id based on the existing role name and tenant context
UPDATE users u
SET role_id = (
    SELECT r.id FROM roles r
    WHERE r.name = u.role
    AND (r.tenant_id = u.tenant_id OR (r.tenant_id IS NULL AND u.tenant_id IS NULL))
    LIMIT 1
);

-- 4. Add the foreign key constraint
ALTER TABLE users ADD CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id);

-- 5. Add an index for the role_id to optimize JOINs and lookups
CREATE INDEX idx_users_role_id ON users(role_id);

-- 6. Drop the old string-based role column
ALTER TABLE users DROP COLUMN role;
