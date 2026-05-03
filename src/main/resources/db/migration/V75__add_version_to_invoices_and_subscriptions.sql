-- V75: Add version column for optimistic locking to invoices and subscriptions
ALTER TABLE invoices
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE subscriptions
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;