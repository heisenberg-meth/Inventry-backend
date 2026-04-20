-- ============================================
-- V4__payments_and_sequences.sql
-- Payments table and tenant sequences
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'payments') THEN
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
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_payments_tenant') THEN
        CREATE INDEX idx_payments_tenant ON payments(tenant_id);
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_payments_invoice') THEN
        CREATE INDEX idx_payments_invoice ON payments(invoice_id);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'invoice_sequence') THEN
        ALTER TABLE tenants ADD COLUMN invoice_sequence INTEGER DEFAULT 0;
    END IF;
END $$;
