-- ============================================
-- V68__add_subscription_trial_end.sql
-- Support for trial period tracking (FR-04)
-- ============================================
ALTER TABLE subscriptions
ADD COLUMN trial_end TIMESTAMP WITH TIME ZONE;