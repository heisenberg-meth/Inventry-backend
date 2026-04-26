-- ============================================
-- V54__add_2fa_fields_to_users.sql
-- Adding support for Two-Factor Authentication (TOTP)
-- ============================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS backup_codes TEXT; -- JSON array of backup codes
