-- Rename domain to workspace_slug in tenants table
ALTER TABLE tenants RENAME COLUMN domain TO workspace_slug;
