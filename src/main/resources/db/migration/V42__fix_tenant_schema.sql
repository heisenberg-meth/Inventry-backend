-- Add missing columns to tenants table to align with Tenant entity
-- This fixes the 'column updated_at does not exist' error
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
