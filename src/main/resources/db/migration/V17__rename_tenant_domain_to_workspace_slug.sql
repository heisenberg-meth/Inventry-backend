-- Rename domain to workspace_slug in tenants table idempotently
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name = 'tenants' AND column_name = 'domain') THEN
        ALTER TABLE tenants RENAME COLUMN domain TO workspace_slug;
    END IF;
END $$;
