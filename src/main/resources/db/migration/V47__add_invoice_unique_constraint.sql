-- Add unique constraint to invoice_number to prevent duplicates at the DB level
-- This is a safety net for the atomic sequence generation
ALTER TABLE invoices ADD CONSTRAINT uk_invoice_number UNIQUE (invoice_number);
