-- ============================================
-- V4__payments_and_sequences.sql
-- Payments table and tenant sequences
-- ============================================

CREATE TABLE payments (
  id             BIGSERIAL PRIMARY KEY,
  tenant_id      BIGINT        NOT NULL REFERENCES tenants(id),
  invoice_id     BIGINT        NOT NULL REFERENCES invoices(id),
  amount         DECIMAL(12,2) NOT NULL,
  payment_mode   VARCHAR(30),
  reference      VARCHAR(100),
  notes          TEXT,
  created_by     BIGINT        REFERENCES users(id),
  created_at     TIMESTAMP     DEFAULT NOW()
);
CREATE INDEX idx_payments_tenant  ON payments(tenant_id);
CREATE INDEX idx_payments_invoice ON payments(invoice_id);

ALTER TABLE tenants ADD COLUMN invoice_sequence INTEGER DEFAULT 0;
