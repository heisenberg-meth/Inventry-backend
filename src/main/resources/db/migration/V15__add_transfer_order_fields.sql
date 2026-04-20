-- ============================================
-- V15__add_transfer_order_fields.sql
-- Add missing product and quantity fields to transfer_orders
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'transfer_orders' AND column_name = 'product_id') THEN
        ALTER TABLE transfer_orders ADD COLUMN product_id BIGINT REFERENCES products(id);
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'transfer_orders' AND column_name = 'quantity') THEN
        ALTER TABLE transfer_orders ADD COLUMN quantity INT DEFAULT 1;
    END IF;
END $$;
