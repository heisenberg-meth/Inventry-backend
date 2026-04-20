-- V35__add_version_to_products.sql
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'products' AND column_name = 'version') THEN
        ALTER TABLE products ADD COLUMN version BIGINT DEFAULT 0;
    END IF;
END $$;