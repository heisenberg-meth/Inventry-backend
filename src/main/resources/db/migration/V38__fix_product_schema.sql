-- Add is_active column to products table (required by Product entity)
ALTER TABLE products
ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;
-- Add missing columns for full PRD compliance
ALTER TABLE products
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();
ALTER TABLE products
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();