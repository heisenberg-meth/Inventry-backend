-- ============================================
-- V37__optimize_sequences_for_batching.sql
-- Adjust sequences for JDBC batching support
-- ============================================

-- Increment by 50 to match Hibernate batch size for better performance
ALTER SEQUENCE IF EXISTS order_items_id_seq INCREMENT BY 50;
ALTER SEQUENCE IF EXISTS orders_id_seq INCREMENT BY 50;

-- Ensure sequences are synced with current max values if any data exists
-- This is a safety measure (Postgres specific)
-- SELECT setval('order_items_id_seq', COALESCE((SELECT MAX(id) FROM order_items), 1));
-- SELECT setval('orders_id_seq', COALESCE((SELECT MAX(id) FROM orders), 1));
