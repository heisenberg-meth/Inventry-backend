-- Add idempotency_key to orders table for retry-safe order creation
ALTER TABLE orders
ADD COLUMN idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX uk_orders_tenant_idempotency_key ON orders(tenant_id, idempotency_key);