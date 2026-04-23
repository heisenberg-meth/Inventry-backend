-- Add index for user counting performance per tenant
CREATE INDEX idx_user_tenant_active ON users (tenant_id, is_active);
