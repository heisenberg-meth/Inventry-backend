-- V53__restore_product_stock_columns.sql
-- Restore stock and reorder_level columns to products table
-- These columns were moved to inventory in V51 but tests and Product entity still use them directly
-- This migration is safe to run even if V51 hasn't created the inventory table
-- Add columns if they don't exist
ALTER TABLE products
ADD COLUMN IF NOT EXISTS stock INTEGER NOT NULL DEFAULT 0;
ALTER TABLE products
ADD COLUMN IF NOT EXISTS reorder_level INTEGER DEFAULT 10;
-- Copy data from inventory if the table exists and has data
-- This uses a separate statement that won't fail if inventory doesn't exist
DO $$ BEGIN IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_name = 'inventory'
) THEN
UPDATE products p
SET stock = COALESCE(i.quantity, 0),
    reorder_level = COALESCE(i.reorder_level, 10)
FROM inventory i
WHERE p.id = i.product_id
    AND i.tenant_id = p.tenant_id;
END IF;
END $$;
-- Set default values
ALTER TABLE products
ALTER COLUMN stock
SET DEFAULT 0;
ALTER TABLE products
ALTER COLUMN reorder_level
SET DEFAULT 10;