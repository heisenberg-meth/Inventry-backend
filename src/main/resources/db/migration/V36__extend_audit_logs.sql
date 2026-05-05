ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_type VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_id BIGINT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS old_value TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_value TEXT;

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);