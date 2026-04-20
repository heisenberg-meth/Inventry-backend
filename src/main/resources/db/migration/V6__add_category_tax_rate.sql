-- ============================================
-- V6__add_category_tax_rate.sql
-- Add tax_rate to categories table.
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'categories' AND column_name = 'tax_rate') THEN
        ALTER TABLE categories ADD COLUMN tax_rate DECIMAL(5,2) DEFAULT 0.00;
    END IF;
END $$;
