-- V34__add_version_to_users_and_tenants.sql
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'version') THEN
        ALTER TABLE users ADD COLUMN version BIGINT DEFAULT 0;
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'version') THEN
        ALTER TABLE tenants ADD COLUMN version BIGINT DEFAULT 0;
    END IF;
END $$;
