-- ============================================
-- V42__add_order_performance_indexes.sql
-- Optimizing order analytics queries
-- ============================================

DO $$ 
BEGIN 
    -- Index for revenue trend aggregations
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_orders_tenant_type_date') THEN
        CREATE INDEX idx_orders_tenant_type_date ON orders(tenant_id, type, created_at);
    END IF;

    -- Index for order status statistics
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_orders_tenant_status_date') THEN
        CREATE INDEX idx_orders_tenant_status_date ON orders(tenant_id, status, created_at);
    END IF;
END $$;
