-- ============================================
-- V8__add_tenant_limits.sql
-- Add product and user limits to tenants
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'max_products') THEN
        ALTER TABLE tenants ADD COLUMN max_products INTEGER;
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'max_users') THEN
        ALTER TABLE tenants ADD COLUMN max_users INTEGER;
    END IF;
END $$;

-- Set default values for existing tenants
-- FREE: 50 products, 2 users
-- SILVER: 500 products, 10 users
-- GOLD: NULL (unlimited)

UPDATE tenants SET max_products = 50, max_users = 2 WHERE plan = 'FREE';
UPDATE tenants SET max_products = 500, max_users = 10 WHERE plan = 'SILVER';
UPDATE tenants SET max_products = NULL, max_users = NULL WHERE plan = 'GOLD';
