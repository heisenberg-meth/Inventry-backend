-- ============================================
-- V55__add_ip_whitelist_to_tenants.sql
-- Adding support for IP Whitelisting at tenant level
-- ============================================

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS ip_whitelist TEXT; -- JSON array of allowed CIDRs or IPs
