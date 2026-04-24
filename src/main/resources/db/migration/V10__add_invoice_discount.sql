-- V10__add_invoice_discount.sql
-- Add discount column to invoices table to match JPA entity

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS discount DECIMAL(12,2) DEFAULT 0;

