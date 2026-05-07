-- V60__product_domain_phase24_gaps.sql
-- Phase 24: Product Domain - Close remaining gaps

-- 1. CHECK constraint: reorder_level must be >= 0
ALTER TABLE products ADD CONSTRAINT chk_reorder_level_positive
CHECK (reorder_level >= 0);

-- 2. Explicit NULL-safe purchase_price constraint (replace existing)
ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_purchase_price_positive;
ALTER TABLE products ADD CONSTRAINT chk_purchase_price_positive
CHECK (purchase_price IS NULL OR purchase_price >= 0);

-- 3. Include barcode in search vector trigger (was missing)
CREATE OR REPLACE FUNCTION products_search_vector_update() RETURNS trigger AS $$
BEGIN
    new.search_vector := to_tsvector('english',
        coalesce(new.name,'') || ' ' ||
        coalesce(new.description,'') || ' ' ||
        coalesce(new.sku,'') || ' ' ||
        coalesce(new.barcode,''));
    RETURN new;
END
$$ LANGUAGE plpgsql;

-- Rebuild existing search vectors to include barcode
UPDATE products SET search_vector = to_tsvector('english',
    coalesce(name,'') || ' ' ||
    coalesce(description,'') || ' ' ||
    coalesce(sku,'') || ' ' ||
    coalesce(barcode,''));

-- 4. Index for tenant_id + is_deleted queries (replaces old is_active pattern)
CREATE INDEX IF NOT EXISTS idx_products_tenant_deleted
ON products (tenant_id, is_deleted);

-- 5. Composite index for active products listing
CREATE INDEX IF NOT EXISTS idx_products_tenant_active_listing
ON products (tenant_id, is_deleted, id)
WHERE is_deleted = false;
