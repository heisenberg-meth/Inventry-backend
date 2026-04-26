-- Add impersonated_by column to audit_logs for production-grade traceability
ALTER TABLE audit_logs ADD COLUMN impersonated_by BIGINT;

-- Add index to support auditing impersonator actions
CREATE INDEX idx_audit_logs_impersonated_by ON audit_logs(impersonated_by);
