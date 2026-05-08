-- V46__add_is_active_to_tenants.sql
ALTER TABLE tenants
ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;