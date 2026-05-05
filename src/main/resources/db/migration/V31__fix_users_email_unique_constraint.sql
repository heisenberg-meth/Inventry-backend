-- V26__fix_users_email_unique_constraint.sql
-- Make users.email unique per tenant (true multi-tenant behavior)
-- Drop the global unique constraint and add tenant-scoped unique index
-- First, drop the global unique constraint (if exists from V1)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
-- Add tenant-scoped unique constraint
CREATE UNIQUE INDEX idx_users_tenant_email ON users(tenant_id, email);