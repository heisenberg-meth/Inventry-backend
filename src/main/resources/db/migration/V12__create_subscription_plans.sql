-- ============================================
-- V12__create_subscription_plans.sql
-- Subscription plan management
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'subscription_plans') THEN
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
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'subscriptions') THEN
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
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_subscriptions_tenant') THEN
        CREATE INDEX idx_subscriptions_tenant ON subscriptions(tenant_id);
    END IF;
END $$;
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_subscriptions_status') THEN
        CREATE INDEX idx_subscriptions_status ON subscriptions(status);
    END IF;
END $$;
