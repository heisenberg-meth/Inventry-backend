-- FIX 1: Add version column for optimistic locking to remaining tables
ALTER TABLE roles
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE orders
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE customers
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE suppliers
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE support_tickets
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE pharmacy_products
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE warehouse_products
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
-- FIX 2: Add tenant_id column for strict isolation to child/extension tables
-- order_items
ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
-- support_tickets already has tenant_id but let's ensure it's not nullable
ALTER TABLE support_tickets
ALTER COLUMN tenant_id
SET NOT NULL;
-- pharmacy_products
ALTER TABLE pharmacy_products
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
-- warehouse_products
ALTER TABLE warehouse_products
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
-- Add indexes for performance and isolation
CREATE INDEX IF NOT EXISTS idx_order_items_tenant_id ON order_items(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pharmacy_products_tenant_id ON pharmacy_products(tenant_id);
CREATE INDEX IF NOT EXISTS idx_warehouse_products_tenant_id ON warehouse_products(tenant_id);