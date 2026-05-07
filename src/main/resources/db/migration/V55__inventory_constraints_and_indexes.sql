-- V55__inventory_constraints_and_indexes.sql
-- Ensure inventory constraints per Phase 6 requirements

-- 1. Ensure check constraint for non-negative inventory
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'inventory') THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.constraint_column_usage 
            WHERE table_name = 'inventory' AND constraint_name = 'chk_inventory_non_negative'
        ) THEN
            ALTER TABLE inventory ADD CONSTRAINT chk_inventory_non_negative
            CHECK (quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= quantity);
        END IF;
    END IF;
END
$$;

-- 2. Add unique constraint for product_id per tenant (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_inventory_tenant_product'
    ) THEN
        ALTER TABLE inventory ADD CONSTRAINT uk_inventory_tenant_product 
        UNIQUE (tenant_id, product_id);
    END IF;
END
$$;

-- 3. Add composite index for tenant + product lookups
CREATE INDEX IF NOT EXISTS idx_inventory_tenant_product 
ON inventory (tenant_id, product_id);

-- 4. Add index for low stock queries
CREATE INDEX IF NOT EXISTS idx_inventory_low_stock 
ON inventory (tenant_id, low_stock_threshold) 
WHERE low_stock_threshold IS NOT NULL;

-- 5. Add index for reorder level queries
CREATE INDEX IF NOT EXISTS idx_inventory_reorder_level 
ON inventory (tenant_id, reorder_level) 
WHERE reorder_level IS NOT NULL;