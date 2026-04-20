-- Add GSTIN to customers table
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'customers' AND column_name = 'gstin') THEN
        ALTER TABLE customers ADD COLUMN gstin VARCHAR(20);
    END IF;
END $$;
