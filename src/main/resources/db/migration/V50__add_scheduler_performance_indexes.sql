-- ============================================
-- V50__add_scheduler_performance_indexes.sql
-- Optimizing background scanning tasks
-- ============================================

-- Index for stock scans across all tenants
CREATE INDEX IF NOT EXISTS idx_products_stock_reorder ON products(stock, reorder_level, is_active);

-- Index for overdue invoice scans
CREATE INDEX IF NOT EXISTS idx_invoices_status_due ON invoices(status, due_date);

-- Index for expired token cleanup
CREATE INDEX IF NOT EXISTS idx_users_reset_expiry ON users(reset_token_expiry) WHERE reset_token IS NOT NULL;
