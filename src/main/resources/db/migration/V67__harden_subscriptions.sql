-- ============================================
-- V67__harden_subscriptions.sql
-- Subscriptions and plans hardening (FR-04)
-- ============================================
-- FR-04-B: Uniqueness for tenant subscriptions
-- Enforce "one tenant -> one subscription"
ALTER TABLE subscriptions
ADD CONSTRAINT uk_subscriptions_tenant UNIQUE (tenant_id);
-- FR-04-A: Default plan flag
ALTER TABLE subscription_plans
ADD COLUMN is_default BOOLEAN DEFAULT FALSE;
-- Ensure only one plan is default
CREATE UNIQUE INDEX uk_subscription_plans_default ON subscription_plans (is_default)
WHERE is_default = TRUE;
-- Set a default plan if none exists (assuming TRIAL or FREE exists)
UPDATE subscription_plans
SET is_default = TRUE
WHERE name = 'TRIAL';
UPDATE subscription_plans
SET is_default = TRUE
WHERE name = 'FREE'
    AND NOT EXISTS (
        SELECT 1
        FROM subscription_plans
        WHERE is_default = TRUE
    );