-- V48__add_version_to_suppliers.sql
ALTER TABLE suppliers
ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
