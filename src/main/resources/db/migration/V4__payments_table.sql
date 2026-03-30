-- Record: AGENT TASK 32
-- Add invoice sequence to tenants
ALTER TABLE tenants ADD COLUMN invoice_sequence INTEGER DEFAULT 0;

-- Create payments table
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

-- Indexing for performance and multi-tenancy
CREATE INDEX idx_payments_tenant  ON payments(tenant_id);
CREATE INDEX idx_payments_invoice ON payments(id, invoice_id);
