-- ============================================
-- V21__add_tenant_business_details.sql
-- Add address and gstin to tenants for professional invoicing
-- ============================================

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS address TEXT;

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS gstin VARCHAR(20);

