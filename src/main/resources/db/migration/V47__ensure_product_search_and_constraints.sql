-- V47__ensure_product_search_and_constraints.sql
-- Ensure search_vector column exists
ALTER TABLE products
ADD COLUMN IF NOT EXISTS search_vector tsvector;
-- Create or replace the trigger function
CREATE OR REPLACE FUNCTION products_search_vector_update() RETURNS trigger AS $$ BEGIN new.search_vector := to_tsvector(
        'english',
        coalesce(new.name, '') || ' ' || coalesce(new.description, '') || ' ' || coalesce(new.sku, '')
    );
RETURN new;
END $$ LANGUAGE plpgsql;
-- Re-create the trigger
DROP TRIGGER IF EXISTS trg_products_search_vector_update ON products;
CREATE TRIGGER trg_products_search_vector_update BEFORE
INSERT
    OR
UPDATE ON products FOR EACH ROW EXECUTE FUNCTION products_search_vector_update();
-- Populate search_vector
UPDATE products
SET search_vector = to_tsvector(
        'english',
        coalesce(name, '') || ' ' || coalesce(description, '') || ' ' || coalesce(sku, '')
    )
WHERE search_vector IS NULL;
-- Ensure unique index for SKU per Tenant
DROP INDEX IF EXISTS ux_product_tenant_sku;
CREATE UNIQUE INDEX ux_product_tenant_sku ON products (tenant_id, sku)
WHERE is_deleted = false;