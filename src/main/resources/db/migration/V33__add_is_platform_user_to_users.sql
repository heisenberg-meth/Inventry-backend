-- V33__add_is_platform_user_to_users.sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_platform_user BOOLEAN DEFAULT FALSE;

