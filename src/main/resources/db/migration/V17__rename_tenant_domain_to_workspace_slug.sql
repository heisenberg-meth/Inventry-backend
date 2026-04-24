-- Add workspace_slug if it doesn't exist
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS workspace_slug VARCHAR(255);

-- Ensure domain exists (even if missing in some environments) to avoid "Column not found" errors during UPDATE
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS domain VARCHAR(255);

-- Copy data from domain to workspace_slug if workspace_slug is null
UPDATE tenants SET workspace_slug = domain WHERE workspace_slug IS NULL;

-- Drop domain if it exists
ALTER TABLE tenants DROP COLUMN IF EXISTS domain;
