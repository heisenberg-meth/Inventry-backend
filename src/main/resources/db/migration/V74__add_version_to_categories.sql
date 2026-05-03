-- V74__add_version_to_categories.sql
-- Add version column for optimistic locking to categories table
ALTER TABLE categories
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;