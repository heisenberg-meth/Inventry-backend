-- Add idempotency_key to tenants table for retry-safe signup
ALTER TABLE tenants ADD COLUMN idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX uk_tenants_idempotency_key ON tenants(idempotency_key);
