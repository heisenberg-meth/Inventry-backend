-- ============================================
-- V41__add_product_performance_indexes.sql
-- Optimizing dashboard and aggregation queries
-- ============================================

DO $$ 
BEGIN 
    -- Index for fast filtering by tenant and active status (Dashboards)
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_tenant_active') THEN
        CREATE INDEX idx_products_tenant_active ON products(tenant_id, is_active);
    END IF;

    -- Index for category-based aggregations
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_tenant_category') THEN
        CREATE INDEX idx_products_tenant_category ON products(tenant_id, category_id, is_active);
    END IF;

    -- Index for stock-based sorting and alerts
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_tenant_stock') THEN
        CREATE INDEX idx_products_tenant_stock ON products(tenant_id, stock, reorder_level);
    END IF;
END $$;
