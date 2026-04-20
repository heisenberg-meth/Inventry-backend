-- Create alerts table
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'alerts') THEN
        CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL, -- LOW_STOCK, EXPIRY, OVERDUE_INVOICE, SUBSCRIPTION_EXPIRY
    severity VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    message TEXT NOT NULL,
    resource_id BIGINT,
    is_dismissed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    dismissed_at TIMESTAMP
);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_alerts_tenant') THEN
        CREATE INDEX idx_alerts_tenant ON alerts(tenant_id);
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_alerts_type') THEN
        CREATE INDEX idx_alerts_type ON alerts(type);
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_alerts_is_dismissed') THEN
        CREATE INDEX idx_alerts_is_dismissed ON alerts(is_dismissed);
    END IF;
END $$;
