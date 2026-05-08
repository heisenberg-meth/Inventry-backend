-- V39__add_tenant_id_to_order_items_add_version_columns_add_constraints.sql
-- Add tenant_id to order_items (required for tenant isolation)
ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
-- Populate tenant_id from parent order
UPDATE order_items oi
SET tenant_id = o.tenant_id
FROM orders o
WHERE oi.order_id = o.id;
-- Make tenant_id NOT NULL after population
ALTER TABLE order_items
ALTER COLUMN tenant_id
SET NOT NULL;
-- Add created_at and updated_at to order_items
ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();
ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
-- Add version column to order_items for optimistic locking
ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
-- Add version column to entities missing it
ALTER TABLE orders
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE invoices
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE customers
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE roles
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE categories
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE stock_movements
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE transfer_orders
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE notifications
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE alerts
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE webhooks
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE support_tickets
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE support_messages
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE support_attachments
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE subscriptions
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE payment_gateway_logs
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE subscription_plans
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
-- Add is_deleted to customers (was missing)
ALTER TABLE customers
ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;
-- Add updated_at to entities missing it
ALTER TABLE tenants
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE users
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE orders
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE invoices
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE customers
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE roles
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE stock_movements
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE transfer_orders
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE notifications
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE alerts
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE webhooks
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE support_messages
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE support_attachments
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE subscriptions
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
ALTER TABLE payment_gateway_logs
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
-- Unique constraint: customer email per tenant
CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_tenant_email ON customers (tenant_id, email)
WHERE is_deleted = false;
-- Unique constraint: supplier email per tenant
CREATE UNIQUE INDEX IF NOT EXISTS ux_supplier_tenant_email ON suppliers (tenant_id, email)
WHERE is_deleted = false;
-- Tenant-scoped indexes for order_items
CREATE INDEX IF NOT EXISTS idx_order_items_tenant_order ON order_items (tenant_id, order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_tenant_product ON order_items (tenant_id, product_id);
-- Tenant-scoped indexes for notifications
CREATE INDEX IF NOT EXISTS idx_notifications_tenant_user ON notifications (tenant_id, user_id);
-- Tenant-scoped indexes for alerts
CREATE INDEX IF NOT EXISTS idx_alerts_tenant_dismissed ON alerts (tenant_id, is_dismissed);
-- Tenant-scoped indexes for webhooks
CREATE INDEX IF NOT EXISTS idx_webhooks_tenant_active ON webhooks (tenant_id, is_active);