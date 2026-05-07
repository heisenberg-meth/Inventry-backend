-- V61__order_items_phase28_gaps.sql
-- Phase 28: Order Items - Close remaining gaps

-- 1. Rename 'total' column to 'subtotal' for accuracy
ALTER TABLE order_items RENAME COLUMN total TO subtotal;

-- 2. CHECK constraint: quantity must be > 0
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_quantity_positive
CHECK (quantity > 0);

-- 3. CHECK constraint: unit_price must be >= 0
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_unit_price_positive
CHECK (unit_price >= 0);

-- 4. CHECK constraint: discount must be >= 0
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_discount_positive
CHECK (discount >= 0);

-- 5. CHECK constraint: tax_rate must be >= 0
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_tax_rate_positive
CHECK (tax_rate >= 0);

-- 6. CHECK constraint: subtotal must be >= 0
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_subtotal_positive
CHECK (subtotal >= 0);

-- 7. FK constraint: order_items.tenant_id → tenants.id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_order_items_tenant' AND table_name = 'order_items'
    ) THEN
        ALTER TABLE order_items ADD CONSTRAINT fk_order_items_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);
    END IF;
END
$$;

-- 8. Index: tenant_id + order_id composite
CREATE INDEX IF NOT EXISTS idx_order_items_tenant_order
ON order_items (tenant_id, order_id);

-- 9. Index: tenant_id + product_id composite
CREATE INDEX IF NOT EXISTS idx_order_items_tenant_product
ON order_items (tenant_id, product_id);
