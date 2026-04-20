-- Add gateway fields to payments
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'payments' AND column_name = 'gateway_transaction_id') THEN
        ALTER TABLE payments ADD COLUMN gateway_transaction_id VARCHAR(255);
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'payments' AND column_name = 'status') THEN
        ALTER TABLE payments ADD COLUMN status VARCHAR(50) DEFAULT 'PENDING';
    END IF;
END $$;

-- Create payment_gateway_logs table
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'payment_gateway_logs') THEN
        CREATE TABLE payment_gateway_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payment_id BIGINT REFERENCES payments(id),
    event_type VARCHAR(100),
    raw_payload TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_pg_logs_tenant') THEN
        CREATE INDEX idx_pg_logs_tenant ON payment_gateway_logs(tenant_id);
    END IF;
END $$;
