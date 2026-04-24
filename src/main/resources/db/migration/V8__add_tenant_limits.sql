-- ============================================
-- V8__add_tenant_limits.sql
-- Add product and user limits to tenants
-- ============================================

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS max_products INTEGER;

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS max_users INTEGER;

-- Set default values for existing tenants
-- FREE: 50 products, 2 users
-- SILVER: 500 products, 10 users
-- GOLD: NULL (unlimited)

UPDATE tenants SET max_products = 50, max_users = 2 WHERE plan = 'FREE';
UPDATE tenants SET max_products = 500, max_users = 10 WHERE plan = 'SILVER';
UPDATE tenants SET max_products = NULL, max_users = NULL WHERE plan = 'GOLD';
