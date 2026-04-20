-- Create email_verifications table
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'email_verifications') THEN
        CREATE TABLE email_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
    END IF;
END $$;

-- Add is_verified column to users
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'is_verified') THEN
        ALTER TABLE users ADD COLUMN is_verified BOOLEAN DEFAULT FALSE;
    END IF;
END $$;

-- Update existing users to be verified (optional, for backward compatibility)
UPDATE users SET is_verified = TRUE;
