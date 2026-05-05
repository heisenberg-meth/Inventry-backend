-- Drop redundant is_active column from products table
-- We are standardizing on is_deleted as per PRD Section 4.1.3
ALTER TABLE products DROP COLUMN IF EXISTS is_active;
