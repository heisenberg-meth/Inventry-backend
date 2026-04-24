-- ============================================
-- V5__add_user_scope.sql
-- Add scope to users table to separate platform and tenant users.
-- ============================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS scope VARCHAR(20) DEFAULT 'TENANT';

CREATE INDEX IF NOT EXISTS idx_users_scope ON users(scope);

