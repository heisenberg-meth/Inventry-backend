-- ============================================
-- V42__add_order_performance_indexes.sql
-- Optimizing order analytics queries
-- ============================================

-- Index for revenue trend aggregations
    CREATE INDEX IF NOT EXISTS idx_orders_tenant_type_date ON orders(tenant_id, type, created_at);

    -- Index for order status statistics
    CREATE INDEX IF NOT EXISTS idx_orders_tenant_status_date ON orders(tenant_id, status, created_at);

