-- Convert old_value and new_value columns to JSONB for better structured data tracking
-- As per PRD Section 3.7
ALTER TABLE audit_logs 
  ALTER COLUMN old_value TYPE JSONB USING old_value::JSONB,
  ALTER COLUMN new_value TYPE JSONB USING new_value::JSONB;
