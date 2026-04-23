-- Add webhook_secret to tenants and event_id to payment_gateway_logs for security and idempotency
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tenants' AND column_name = 'webhook_secret') THEN
        ALTER TABLE tenants ADD COLUMN webhook_secret VARCHAR(255);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'payment_gateway_logs' AND column_name = 'event_id') THEN
        ALTER TABLE payment_gateway_logs ADD COLUMN event_id VARCHAR(100);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_pg_logs_event_id') THEN
        CREATE UNIQUE INDEX idx_pg_logs_event_id ON payment_gateway_logs(event_id);
    END IF;
END $$;
