-- V69__add_currency_to_tenants.sql
-- Add currency column to tenants table to match JPA entity
ALTER TABLE tenants
ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'INR';