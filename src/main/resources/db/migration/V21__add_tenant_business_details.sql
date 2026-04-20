-- ============================================
-- V21__add_tenant_business_details.sql
-- Add address and gstin to tenants for professional invoicing
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'address') THEN
        ALTER TABLE tenants ADD COLUMN address TEXT;
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'gstin') THEN
        ALTER TABLE tenants ADD COLUMN gstin VARCHAR(20);
    END IF;
END $$;
