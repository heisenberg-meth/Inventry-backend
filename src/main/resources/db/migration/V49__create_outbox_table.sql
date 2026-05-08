-- V49__create_outbox_table.sql
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id TEXT NOT NULL,
    type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING, SENT, FAILED
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    processed_at TIMESTAMP
);
CREATE INDEX idx_outbox_status ON outbox_event(status);
CREATE INDEX idx_outbox_created_at ON outbox_event(created_at);