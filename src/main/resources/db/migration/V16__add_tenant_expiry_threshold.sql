-- ============================================
-- V16__add_tenant_expiry_threshold.sql
-- Add configurable expiry threshold to tenants
-- ============================================

ALTER TABLE tenants ADD COLUMN expiry_threshold_days INTEGER DEFAULT 30;
