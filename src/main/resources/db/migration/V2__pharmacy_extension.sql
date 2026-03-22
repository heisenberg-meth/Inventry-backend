-- ============================================
-- V2__pharmacy_extension.sql
-- Pharmacy-specific extension tables
-- ============================================

CREATE TABLE pharmacy_products (
  product_id    BIGINT       PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
  batch_number  VARCHAR(100),
  expiry_date   DATE         NOT NULL,
  manufacturer  VARCHAR(255),
  hsn_code      VARCHAR(50),
  schedule      VARCHAR(10)
);
CREATE INDEX idx_pharmacy_expiry ON pharmacy_products(expiry_date);
