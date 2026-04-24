-- Add gateway fields to payments
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway_transaction_id VARCHAR(255);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'PENDING';

-- Create payment_gateway_logs table
CREATE TABLE IF NOT EXISTS payment_gateway_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payment_id BIGINT REFERENCES payments(id),
    event_type VARCHAR(100),
    raw_payload TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pg_logs_tenant ON payment_gateway_logs(tenant_id);

