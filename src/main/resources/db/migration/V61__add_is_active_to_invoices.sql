-- Add is_active flag to invoices table
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;