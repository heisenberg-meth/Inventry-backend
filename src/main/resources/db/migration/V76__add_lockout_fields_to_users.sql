-- V76__add_lockout_fields_to_users.sql
-- Add failed_attempts and lockout_until columns for DB-based account lockout
ALTER TABLE users
ADD COLUMN IF NOT EXISTS failed_attempts INT DEFAULT 0,
ADD COLUMN IF NOT EXISTS lockout_until TIMESTAMP;
