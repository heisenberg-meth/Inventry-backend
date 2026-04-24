-- V35__add_version_to_products.sql
ALTER TABLE products ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
    
