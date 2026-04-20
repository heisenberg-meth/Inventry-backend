-- ============================================
-- V16__add_tenant_expiry_threshold.sql
-- Add configurable expiry threshold to tenants
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'expiry_threshold_days') THEN
        ALTER TABLE tenants ADD COLUMN expiry_threshold_days INTEGER DEFAULT 30;
    END IF;
END $$;
