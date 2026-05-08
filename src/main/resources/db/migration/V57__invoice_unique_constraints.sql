-- V57__invoice_unique_constraints.sql
-- Ensure invoice number uniqueness per tenant
-- Add unique constraint on invoice_number per tenant
DO $$ BEGIN IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'uk_invoice_tenant_number'
) THEN
ALTER TABLE invoices
ADD CONSTRAINT uk_invoice_tenant_number UNIQUE (tenant_id, invoice_number);
END IF;
END $$;
-- Add index for faster invoice lookups
CREATE INDEX IF NOT EXISTS idx_invoices_tenant_order ON invoices (tenant_id, order_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_invoices_due_date ON invoices (tenant_id, due_date);