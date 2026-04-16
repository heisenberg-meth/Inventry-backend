-- Add reference_order_id to orders for returns
ALTER TABLE orders ADD COLUMN reference_order_id BIGINT REFERENCES orders(id);

-- Add parent_invoice_id to invoices for credit notes
ALTER TABLE invoices ADD COLUMN parent_invoice_id BIGINT REFERENCES invoices(id);

-- Add RETURN type to any check constraints if they exist (V1 doesn't have check constraints on type, but for documentation)
-- Type can be SALE, PURCHASE, RETURN
