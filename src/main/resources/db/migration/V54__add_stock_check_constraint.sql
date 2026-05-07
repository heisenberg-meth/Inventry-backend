-- V54__add_stock_check_constraint.sql
-- Ensure products.stock never goes below zero
ALTER TABLE products ADD CONSTRAINT chk_products_stock_non_negative CHECK (stock >= 0);

-- If inventory table exists, ensure its quantity also has the constraint (V51 should have added it, but let's be sure)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'inventory') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.constraint_column_usage WHERE table_name = 'inventory' AND constraint_name = 'chk_inventory_quantity') THEN
            ALTER TABLE inventory ADD CONSTRAINT chk_inventory_quantity CHECK (quantity >= 0);
        END IF;
    END IF;
END
$$;
