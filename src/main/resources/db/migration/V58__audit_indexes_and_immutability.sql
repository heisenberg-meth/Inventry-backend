-- V58__audit_indexes_and_immutability.sql
-- Audit trail indexes and immutability protection
-- 1. Add request_id column if not exists
DO $$ BEGIN IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'audit_logs'
        AND column_name = 'request_id'
) THEN
ALTER TABLE audit_logs
ADD COLUMN request_id TEXT;
END IF;
END $$;
-- 2. Indexes for audit_logs
CREATE INDEX IF NOT EXISTS idx_audit_tenant_created ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_entity_type_id ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_request_id ON audit_logs (request_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs (action);
-- 3. Immutability protection - prevent UPDATE and DELETE
-- Using triggers instead of rules (more portable)
-- Drop existing triggers if they exist
DROP TRIGGER IF EXISTS prevent_audit_update ON audit_logs;
DROP TRIGGER IF EXISTS prevent_audit_delete ON audit_logs;
-- Create trigger function
CREATE OR REPLACE FUNCTION prevent_audit_modification() RETURNS TRIGGER AS $$ BEGIN RAISE EXCEPTION 'Audit logs are immutable - modifications are not allowed';
END;
$$ LANGUAGE plpgsql;
-- Create triggers
CREATE TRIGGER prevent_audit_update BEFORE
UPDATE ON audit_logs FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
CREATE TRIGGER prevent_audit_delete BEFORE DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();