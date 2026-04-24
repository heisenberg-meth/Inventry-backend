-- Performance indexes for optimized product queries

-- 1. Performance index for tenant-based filtering and paging
CREATE INDEX IF NOT EXISTS idx_products_tenant_active ON products(tenant_id, is_active);

-- 2. Performance index for stock-based filtering
CREATE INDEX IF NOT EXISTS idx_products_tenant_stock ON products(tenant_id, stock);

-- 3. Paging optimization
CREATE INDEX IF NOT EXISTS idx_products_tenant_id ON products(tenant_id, id);

-- 4. Stock and reorder level monitoring
-- Note: Partial indexes (WHERE clause) are supported by both Postgres and H2 2.0+
CREATE INDEX IF NOT EXISTS idx_products_tenant_stock_reorder ON products(tenant_id, stock, reorder_level, is_active);
