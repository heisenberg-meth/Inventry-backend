-- ============================================
-- V5__add_user_scope.sql
-- Add scope to users table to separate platform and tenant users.
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'scope') THEN
        ALTER TABLE users ADD COLUMN scope VARCHAR(20) DEFAULT 'TENANT';
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_users_scope') THEN
        CREATE INDEX idx_users_scope ON users(scope);
    END IF;
END $$;
