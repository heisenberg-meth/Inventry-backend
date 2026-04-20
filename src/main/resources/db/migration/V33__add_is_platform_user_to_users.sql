-- V33__add_is_platform_user_to_users.sql
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'is_platform_user') THEN
        ALTER TABLE users ADD COLUMN is_platform_user BOOLEAN DEFAULT FALSE;
    END IF;
END $$;
