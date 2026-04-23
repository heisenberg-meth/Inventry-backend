-- ============================================
-- V43__add_user_reset_token_index.sql
-- Optimizing scheduled task cleanup queries
-- ============================================

DO $$ 
BEGIN 
    -- Index for fast identification of expired reset tokens
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_users_tenant_reset_token_expiry') THEN
        CREATE INDEX idx_users_tenant_reset_token_expiry ON users(tenant_id, reset_token_expiry);
    END IF;
END $$;
