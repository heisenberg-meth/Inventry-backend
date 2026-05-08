-- Add unique constraint to workspace_slug to prevent race condition duplicates
ALTER TABLE tenants
ADD CONSTRAINT uq_workspace_slug UNIQUE (workspace_slug);