-- Align Product module with PRD Contract
-- 1. Add description column if it doesn't exist
ALTER TABLE products
ADD COLUMN IF NOT EXISTS description TEXT;
-- 2. Rename is_active to is_deleted and flip boolean values
DO $$ BEGIN IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'products'
        AND column_name = 'is_active'
) THEN
ALTER TABLE products
    RENAME COLUMN is_active TO is_deleted;
UPDATE products
SET is_deleted = NOT is_deleted;
ALTER TABLE products
ALTER COLUMN is_deleted
SET DEFAULT FALSE;
END IF;
END $$;
-- 3. Add search_vector column and trigger
ALTER TABLE products
ADD COLUMN IF NOT EXISTS search_vector tsvector;
CREATE OR REPLACE FUNCTION products_search_vector_update() RETURNS trigger AS $$ BEGIN new.search_vector := to_tsvector(
        'english',
        coalesce(new.name, '') || ' ' || coalesce(new.description, '') || ' ' || coalesce(new.sku, '')
    );
RETURN new;
END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_products_search_vector_update ON products;
CREATE TRIGGER trg_products_search_vector_update BEFORE
INSERT
    OR
UPDATE ON products FOR EACH ROW EXECUTE FUNCTION products_search_vector_update();
-- Initial population
UPDATE products
SET search_vector = to_tsvector(
        'english',
        coalesce(name, '') || ' ' || coalesce(description, '') || ' ' || coalesce(sku, '')
    );
-- 4. GIN Index for Search
CREATE INDEX IF NOT EXISTS idx_product_search ON products USING GIN (search_vector);
-- 5. Constraints
-- Price must be non-negative
ALTER TABLE products
ADD CONSTRAINT chk_sale_price_positive CHECK (sale_price >= 0);
ALTER TABLE products
ADD CONSTRAINT chk_purchase_price_positive CHECK (purchase_price >= 0);
-- Stock must be non-negative
ALTER TABLE products
ADD CONSTRAINT chk_stock_positive CHECK (stock >= 0);
-- 6. Composite Indexes and Unique SKU per Tenant
DROP INDEX IF EXISTS idx_products_sku;
DROP INDEX IF EXISTS idx_products_tenant_active;
DROP INDEX IF EXISTS idx_products_tenant_id;
-- Required Indexes (PRD 4.3.2)
CREATE INDEX IF NOT EXISTS idx_product_tenant_id ON products (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_product_tenant_sku ON products (tenant_id, sku);
-- SKU Uniqueness per Tenant (PRD 4.2 Mandatory Constraints)
DROP INDEX IF EXISTS ux_product_tenant_sku;
CREATE UNIQUE INDEX ux_product_tenant_sku ON products (tenant_id, sku)
WHERE is_deleted = false;