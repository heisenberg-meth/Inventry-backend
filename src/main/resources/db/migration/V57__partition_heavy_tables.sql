-- ============================================
-- V57__partition_heavy_tables.sql
-- Partitioning stock_movements and audit_logs by range (year).
-- Fixed to be idempotent and dependency-safe.
-- ============================================

-- 1. CLEANUP PREVIOUS FAILED ATTEMPTS (Idempotency)
-- --------------------------------------------
-- Detach sequence from old table column if it exists
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'stock_movements_old') THEN
        ALTER TABLE stock_movements_old ALTER COLUMN id DROP DEFAULT;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'audit_logs_old') THEN
        ALTER TABLE audit_logs_old ALTER COLUMN id DROP DEFAULT;
    END IF;
END $$;

-- Drop partitions explicitly to avoid "cascade" dependency issues
DROP TABLE IF EXISTS stock_movements_p2024;
DROP TABLE IF EXISTS stock_movements_p2025;
DROP TABLE IF EXISTS stock_movements_p2026;
DROP TABLE IF EXISTS stock_movements_p_default;
DROP TABLE IF EXISTS audit_logs_p2024;
DROP TABLE IF EXISTS audit_logs_p2025;
DROP TABLE IF EXISTS audit_logs_p2026;
DROP TABLE IF EXISTS audit_logs_p_default;

-- Drop main tables to start fresh in this migration
DROP TABLE IF EXISTS stock_movements;
DROP TABLE IF EXISTS audit_logs;

-- 2. SETUP stock_movements
-- --------------------------------------------
-- Ensure old table exists (if this is a fresh run)
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'stock_movements_old') THEN
        -- This handles the case where the table was never renamed (first run)
        IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'stock_movements') THEN
            ALTER TABLE stock_movements RENAME TO stock_movements_old;
        END IF;
    END IF;
END $$;

CREATE TABLE stock_movements (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    previous_stock INTEGER,
    new_stock INTEGER,
    reference_id BIGINT,
    reference_type VARCHAR(30),
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE stock_movements_p2024 PARTITION OF stock_movements FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
CREATE TABLE stock_movements_p2025 PARTITION OF stock_movements FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
CREATE TABLE stock_movements_p2026 PARTITION OF stock_movements FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE stock_movements_p_default PARTITION OF stock_movements DEFAULT;

CREATE SEQUENCE IF NOT EXISTS stock_movements_id_seq;
SELECT setval('stock_movements_id_seq', COALESCE((SELECT MAX(id) FROM stock_movements_old), 1));
ALTER TABLE stock_movements ALTER COLUMN id SET DEFAULT nextval('stock_movements_id_seq');

INSERT INTO stock_movements SELECT * FROM stock_movements_old;
DROP TABLE stock_movements_old;

-- 3. SETUP audit_logs
-- --------------------------------------------
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'audit_logs_old') THEN
        IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'audit_logs') THEN
            ALTER TABLE audit_logs RENAME TO audit_logs_old;
        END IF;
    END IF;
END $$;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL,
    tenant_id BIGINT,
    user_id BIGINT,
    action VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50),
    resource_id BIGINT,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE audit_logs_p2024 PARTITION OF audit_logs FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
CREATE TABLE audit_logs_p2025 PARTITION OF audit_logs FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
CREATE TABLE audit_logs_p2026 PARTITION OF audit_logs FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE audit_logs_p_default PARTITION OF audit_logs DEFAULT;

CREATE SEQUENCE IF NOT EXISTS audit_logs_id_seq;
SELECT setval('audit_logs_id_seq', COALESCE((SELECT MAX(id) FROM audit_logs_old), 1));
ALTER TABLE audit_logs ALTER COLUMN id SET DEFAULT nextval('audit_logs_id_seq');

INSERT INTO audit_logs (id, tenant_id, user_id, action, details, created_at) 
SELECT id, tenant_id, user_id, action, details, created_at FROM audit_logs_old;
DROP TABLE audit_logs_old;

-- 4. FINAL CONSTRAINTS AND INDEXES
-- --------------------------------------------
ALTER TABLE stock_movements ADD CONSTRAINT fk_sm_product FOREIGN KEY (product_id) REFERENCES products(id);
ALTER TABLE stock_movements ADD CONSTRAINT fk_sm_user FOREIGN KEY (created_by) REFERENCES users(id);
CREATE INDEX idx_stock_movements_tenant ON stock_movements(tenant_id);
CREATE INDEX idx_stock_movements_product ON stock_movements(tenant_id, product_id);

CREATE INDEX idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_date ON audit_logs(created_at);
