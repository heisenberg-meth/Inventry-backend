-- V10__add_invoice_discount.sql
-- Add discount column to invoices table to match JPA entity

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'invoices' AND column_name = 'discount') THEN
        ALTER TABLE invoices ADD COLUMN discount DECIMAL(12,2) DEFAULT 0;
    END IF;
END $$;
