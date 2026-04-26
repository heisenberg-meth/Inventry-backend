-- ============================================
-- V58__create_tenant_summary_materialized_view.sql
-- Materialized view for high-performance dashboard stats
-- ============================================

CREATE MATERIALIZED VIEW IF NOT EXISTS tenant_summary_stats AS
SELECT 
    t.id as tenant_id,
    (SELECT COUNT(*) FROM products p WHERE p.tenant_id = t.id AND p.is_active = true) as total_products,
    (SELECT COUNT(*) FROM products p WHERE p.tenant_id = t.id AND p.is_active = true AND p.stock <= p.reorder_level AND p.stock > 0) as low_stock_count,
    (SELECT COUNT(*) FROM products p WHERE p.tenant_id = t.id AND p.is_active = true AND p.stock = 0) as out_of_stock_count,
    (SELECT COALESCE(SUM(total_amount), 0) FROM orders o WHERE o.tenant_id = t.id AND o.type = 'SALE' AND o.created_at >= CURRENT_DATE) as today_sales_amount,
    (SELECT COUNT(*) FROM orders o WHERE o.tenant_id = t.id AND o.type = 'SALE' AND o.created_at >= CURRENT_DATE) as today_sales_count,
    (SELECT COALESCE(SUM(total_amount), 0) FROM orders o WHERE o.tenant_id = t.id AND o.type = 'PURCHASE' AND o.created_at >= CURRENT_DATE) as today_purchases_amount,
    (SELECT COALESCE(SUM(stock * purchase_price), 0) FROM products p WHERE p.tenant_id = t.id AND p.is_active = true) as inventory_valuation,
    NOW() as last_refreshed_at
FROM tenants t;

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_summary_tenant_id ON tenant_summary_stats(tenant_id);
