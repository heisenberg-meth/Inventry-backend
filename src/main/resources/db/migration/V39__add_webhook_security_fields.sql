-- Add webhook_secret to tenants and event_id to payment_gateway_logs for security and idempotency
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(255);

ALTER TABLE payment_gateway_logs ADD COLUMN IF NOT EXISTS event_id VARCHAR(100);

IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_pg_logs_event_id') THEN
        CREATE UNIQUE INDEX idx_pg_logs_event_id ON payment_gateway_logs(event_id);

