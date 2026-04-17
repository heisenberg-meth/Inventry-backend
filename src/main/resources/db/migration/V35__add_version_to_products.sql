-- V35__add_version_to_products.sql
ALTER TABLE products
ADD COLUMN version BIGINT DEFAULT 0;
