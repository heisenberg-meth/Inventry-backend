-- ============================================
-- V22__add_tenant_company_code.sql
-- Add unique company_code for easy user-facing identity/login
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'company_code') THEN
        ALTER TABLE tenants ADD COLUMN company_code VARCHAR(20);
    END IF;
END $$;

-- Update existing tenants with a temporary code (e.g., T<id>PRFX)
UPDATE tenants SET company_code = 'T' || id || 'FIX' WHERE company_code IS NULL;

-- Now make it NOT NULL and UNIQUE idempotently
DO $$ 
BEGIN 
    -- Set NOT NULL if it's currently nullable
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name = 'tenants' AND column_name = 'company_code' AND is_nullable = 'YES') THEN
        ALTER TABLE tenants ALTER COLUMN company_code SET NOT NULL;
    END IF;

    -- Add UNIQUE constraint if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_tenants_company_code') THEN
        ALTER TABLE tenants ADD CONSTRAINT uk_tenants_company_code UNIQUE (company_code);
    END IF;
END $$;
