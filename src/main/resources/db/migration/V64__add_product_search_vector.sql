-- Add a generated tsvector column for fast full-text search
ALTER TABLE products ADD COLUMN IF NOT EXISTS search_vector tsvector 
GENERATED ALWAYS AS (
    to_tsvector('english', coalesce(name, '') || ' ' || coalesce(sku, '') || ' ' || coalesce(barcode, ''))
) STORED;

-- Create a GIN index on the generated column
CREATE INDEX IF NOT EXISTS idx_products_search_vector ON products USING GIN(search_vector);
