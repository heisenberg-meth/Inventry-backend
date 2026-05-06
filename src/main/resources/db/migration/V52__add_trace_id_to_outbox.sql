-- V52__add_trace_id_to_outbox.sql
ALTER TABLE outbox_event ADD COLUMN trace_id VARCHAR(255);
CREATE INDEX idx_outbox_trace_id ON outbox_event(trace_id);
