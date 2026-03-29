-- ============================================
-- V6__add_category_tax_rate.sql
-- Add tax_rate to categories table.
-- ============================================

ALTER TABLE categories ADD COLUMN tax_rate DECIMAL(5,2) DEFAULT 0.00;
