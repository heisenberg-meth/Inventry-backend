-- ============================================
-- V15__add_transfer_order_fields.sql
-- Add missing product and quantity fields to transfer_orders
-- ============================================

ALTER TABLE transfer_orders ADD COLUMN IF NOT EXISTS product_id BIGINT REFERENCES products(id);

ALTER TABLE transfer_orders ADD COLUMN IF NOT EXISTS quantity INT DEFAULT 1;

