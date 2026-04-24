-- ============================================
-- V41__add_product_performance_indexes.sql
-- Optimizing dashboard and aggregation queries
-- ============================================

-- Index for fast filtering by tenant and active status (Dashboards)
    CREATE INDEX IF NOT EXISTS idx_products_tenant_active ON products(tenant_id, is_active);

    -- Index for category-based aggregations
    CREATE INDEX IF NOT EXISTS idx_products_tenant_category ON products(tenant_id, category_id, is_active);

    -- Index for stock-based sorting and alerts
    CREATE INDEX IF NOT EXISTS idx_products_tenant_stock ON products(tenant_id, stock, reorder_level);

