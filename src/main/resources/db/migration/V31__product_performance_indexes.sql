-- Performance indexes for optimized product queries
CREATE INDEX IF NOT EXISTS idx_products_tenant_id_active ON products(tenant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_products_tenant_id_id ON products(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_products_tenant_stock_reorder ON products(tenant_id, stock, reorder_level) WHERE is_active = true;
