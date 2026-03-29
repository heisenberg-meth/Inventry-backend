-- ============================================
-- V5__add_user_scope.sql
-- Add scope to users table to separate platform and tenant users.
-- ============================================

ALTER TABLE users ADD COLUMN scope VARCHAR(20) DEFAULT 'TENANT';
CREATE INDEX idx_users_scope ON users(scope);
