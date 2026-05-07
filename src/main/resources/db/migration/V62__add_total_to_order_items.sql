-- V62__add_total_to_order_items.sql
-- Add 'total' column back to order_items as the entity expects both 'subtotal' and 'total'.
ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS total NUMERIC(10, 2);
-- Update existing rows to have total = subtotal as a sensible default
UPDATE order_items
SET total = subtotal
WHERE total IS NULL;
-- Make it NOT NULL after population
ALTER TABLE order_items
ALTER COLUMN total
SET NOT NULL;
-- Add check constraint to ensure total is positive
ALTER TABLE order_items
ADD CONSTRAINT chk_order_items_total_positive CHECK (total >= 0);