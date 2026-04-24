-- Add verification token fields to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token VARCHAR(255);

    ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token_expiry TIMESTAMP;

