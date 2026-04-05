-- ============================================
-- V12__create_subscription_plans.sql
-- Subscription plan management
-- ============================================

CREATE TABLE subscription_plans (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR(100) UNIQUE NOT NULL,
  price           DECIMAL(12,2)  DEFAULT 0,
  currency        VARCHAR(10)    DEFAULT 'INR',
  billing_cycle   VARCHAR(20)    NOT NULL,
  duration_days   INTEGER        DEFAULT 30,
  features        TEXT,
  max_users       INTEGER        DEFAULT 0,
  max_products    INTEGER        DEFAULT 0,
  status          VARCHAR(20)    DEFAULT 'ACTIVE',
  version         INTEGER        DEFAULT 1,
  updated_by      BIGINT,
  created_at      TIMESTAMP      DEFAULT NOW(),
  updated_at      TIMESTAMP      DEFAULT NOW()
);

CREATE TABLE subscriptions (
  id              BIGSERIAL PRIMARY KEY,
  tenant_id       BIGINT         NOT NULL REFERENCES tenants(id),
  plan            VARCHAR(100)   NOT NULL,
  status          VARCHAR(20)    NOT NULL,
  start_date      TIMESTAMP      NOT NULL,
  end_date        TIMESTAMP      NOT NULL,
  created_at      TIMESTAMP      DEFAULT NOW(),
  updated_at      TIMESTAMP      DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_tenant ON subscriptions(tenant_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
