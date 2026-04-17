-- Performance indexes for optimized product queries

-- 1. Full-text search index for searchProducts
CREATE INDEX IF NOT EXISTS idx_products_search ON products USING gin(to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(sku, '') || ' ' || COALESCE(barcode, '')));

-- 2. Performance index for tenant-based filtering and paging
CREATE INDEX IF NOT EXISTS idx_products_tenant_active ON products(tenant_id, is_active);

-- 3. Performance index for stock-based filtering
CREATE INDEX IF NOT EXISTS idx_products_tenant_stock ON products(tenant_id, stock);

-- 4. Paging optimization
CREATE INDEX IF NOT EXISTS idx_products_tenant_id ON products(tenant_id, id);

-- 5. Stock and reorder level monitoring
CREATE INDEX IF NOT EXISTS idx_products_tenant_stock_reorder ON products(tenant_id, stock, reorder_level) WHERE is_active = true;
