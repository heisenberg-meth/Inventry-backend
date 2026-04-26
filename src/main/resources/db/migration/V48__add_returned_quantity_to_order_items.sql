-- Add returned_quantity column to order_items
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS returned_quantity INTEGER DEFAULT 0;

-- Optional: Add a check constraint to prevent over-returns at the DB level
ALTER TABLE order_items ADD CONSTRAINT chk_returned_quantity CHECK (returned_quantity <= quantity);

-- Index for linkage integrity and performance
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items(product_id);
