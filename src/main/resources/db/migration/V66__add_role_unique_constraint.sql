-- ============================================
-- V66__add_role_unique_constraint.sql
-- Enforce unique role names per tenant (FR-03-A)
-- ============================================

ALTER TABLE roles ADD CONSTRAINT uk_roles_name_tenant UNIQUE (name, tenant_id);
