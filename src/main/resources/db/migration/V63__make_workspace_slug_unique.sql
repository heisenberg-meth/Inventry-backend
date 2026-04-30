-- Add UNIQUE constraint to workspace_slug in tenants table
ALTER TABLE tenants ADD CONSTRAINT idx_tenants_workspace_slug_unique UNIQUE (workspace_slug);
