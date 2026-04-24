-- Add GSTIN to customers table
ALTER TABLE customers ADD COLUMN IF NOT EXISTS gstin VARCHAR(20);

