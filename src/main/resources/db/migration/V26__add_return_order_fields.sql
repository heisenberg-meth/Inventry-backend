-- Add reference_order_id to orders for returns
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'orders' AND column_name = 'reference_order_id') THEN
        ALTER TABLE orders ADD COLUMN reference_order_id BIGINT REFERENCES orders(id);
    END IF;
END $$;

-- Add parent_invoice_id to invoices for credit notes
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'invoices' AND column_name = 'parent_invoice_id') THEN
        ALTER TABLE invoices ADD COLUMN parent_invoice_id BIGINT REFERENCES invoices(id);
    END IF;
END $$;

-- Add RETURN type to any check constraints if they exist (V1 doesn't have check constraints on type, but for documentation)
-- Type can be SALE, PURCHASE, RETURN
