-- ============================================
-- V51__add_reporting_performance_indexes.sql
-- Optimizing reporting aggregation queries
-- ============================================

-- Index for product inventory valuation and distribution
CREATE INDEX IF NOT EXISTS idx_products_reporting ON products(tenant_id, is_active);

-- Index for sales/purchase aggregation by type and date
CREATE INDEX IF NOT EXISTS idx_orders_reporting ON orders(tenant_id, type, created_at);

-- Index for order status statistics
CREATE INDEX IF NOT EXISTS idx_orders_status_reporting ON orders(tenant_id, status, created_at);
