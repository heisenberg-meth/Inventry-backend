-- ============================================
-- V22__add_tenant_company_code.sql
-- Add unique company_code for easy user-facing identity/login
-- ============================================

ALTER TABLE tenants ADD COLUMN company_code VARCHAR(20);

-- Update existing tenants with a temporary code (e.g., T<id>PRFX)
UPDATE tenants SET company_code = 'T' || id || 'FIX' WHERE company_code IS NULL;

-- Now make it NOT NULL and UNIQUE
ALTER TABLE tenants ALTER COLUMN company_code SET NOT NULL;
ALTER TABLE tenants ADD CONSTRAINT uk_tenants_company_code UNIQUE (company_code);
