-- ============================================
-- V38__enforce_tenant_integrity.sql
-- Enforce database-level integrity for multi-tenancy
-- ============================================

DO $$ 
DECLARE
    fk_name TEXT;
BEGIN 
    -- 1. Ensure users.tenant_id is NOT NULL
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name = 'users' AND column_name = 'tenant_id' AND is_nullable = 'YES') THEN
        ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    -- 2. Update users -> tenants FK with ON DELETE CASCADE
    -- Drop existing if exists (we don't know the exact name from V1, so we find it)
    SELECT tc.constraint_name INTO fk_name
    FROM information_schema.table_constraints AS tc 
    JOIN information_schema.key_column_usage AS kcu
      ON tc.constraint_name = kcu.constraint_name
      AND tc.table_schema = kcu.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY' 
      AND tc.table_name = 'users' 
      AND kcu.column_name = 'tenant_id';

    IF fk_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT ' || fk_name;
    END IF;
    
    -- Re-add with CASCADE
    -- Check if it already exists before adding to prevent errors on retry
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_users_tenant') THEN
        ALTER TABLE users 
        ADD CONSTRAINT fk_users_tenant 
        FOREIGN KEY (tenant_id) 
        REFERENCES tenants(id) 
        ON DELETE CASCADE;
    END IF;

    -- 3. Update categories -> tenants FK with ON DELETE CASCADE
    SELECT tc.constraint_name INTO fk_name
    FROM information_schema.table_constraints AS tc 
    JOIN information_schema.key_column_usage AS kcu
      ON tc.constraint_name = kcu.constraint_name
      AND tc.table_schema = kcu.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY' 
      AND tc.table_name = 'categories' 
      AND kcu.column_name = 'tenant_id';

    IF fk_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE categories DROP CONSTRAINT ' || fk_name;
    END IF;

    -- Re-add with CASCADE
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_categories_tenant') THEN
        ALTER TABLE categories 
        ADD CONSTRAINT fk_categories_tenant 
        FOREIGN KEY (tenant_id) 
        REFERENCES tenants(id) 
        ON DELETE CASCADE;
    END IF;

END $$;
