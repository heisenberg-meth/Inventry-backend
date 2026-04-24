-- ============================================
-- V43__add_user_reset_token_index.sql
-- Optimizing scheduled task cleanup queries
-- ============================================

-- Index for fast identification of expired reset tokens
    CREATE INDEX IF NOT EXISTS idx_users_tenant_reset_token_expiry ON users(tenant_id, reset_token_expiry);

