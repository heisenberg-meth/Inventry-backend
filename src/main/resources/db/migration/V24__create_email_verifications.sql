-- Create email_verifications table
CREATE TABLE email_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Add is_verified column to users
ALTER TABLE users ADD COLUMN is_verified BOOLEAN DEFAULT FALSE;

-- Update existing users to be verified (optional, for backward compatibility)
UPDATE users SET is_verified = TRUE;
