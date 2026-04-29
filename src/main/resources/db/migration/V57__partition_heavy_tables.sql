-- ============================================
-- V57__partition_heavy_tables.sql
-- Partitioning stock_movements and audit_logs by range (year).
-- Fixed to be idempotent and dependency-safe.
-- ============================================

-- 1. CLEANUP PREVIOUS FAILED ATTEMPTS (Idempotency)
-- --------------------------------------------
-- Drop main tables to start fresh in this migration if they are already partitioned
-- We detect partitioning by checking if they exist in pg_partitioned_table
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM pg_partitioned_table p JOIN pg_class c ON p.partrelid = c.oid WHERE c.relname = 'stock_movements') THEN
        DROP TABLE stock_movements CASCADE;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_partitioned_table p JOIN pg_class c ON p.partrelid = c.oid WHERE c.relname = 'audit_logs') THEN
        DROP TABLE audit_logs CASCADE;
    END IF;
END $$;

-- Drop partitions explicitly
DROP TABLE IF EXISTS stock_movements_p2024 CASCADE;
DROP TABLE IF EXISTS stock_movements_p2025 CASCADE;
DROP TABLE IF EXISTS stock_movements_p2026 CASCADE;
DROP TABLE IF EXISTS stock_movements_p_default CASCADE;
DROP TABLE IF EXISTS audit_logs_p2024 CASCADE;
DROP TABLE IF EXISTS audit_logs_p2025 CASCADE;
DROP TABLE IF EXISTS audit_logs_p2026 CASCADE;
DROP TABLE IF EXISTS audit_logs_p_default CASCADE;

-- Rename existing tables if they haven't been renamed yet
DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'stock_movements') AND NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'stock_movements_old') THEN
        ALTER TABLE stock_movements RENAME TO stock_movements_old;
        -- Disassociate sequence ownership so we can drop the old table later
        IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'stock_movements_id_seq') THEN
            ALTER SEQUENCE stock_movements_id_seq OWNED BY NONE;
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'audit_logs') AND NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'audit_logs_old') THEN
        ALTER TABLE audit_logs RENAME TO audit_logs_old;
        IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'audit_logs_id_seq') THEN
            ALTER SEQUENCE audit_logs_id_seq OWNED BY NONE;
        END IF;
    END IF;
END $$;

-- 2. SETUP partitioned stock_movements
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS stock_movements (
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

CREATE TABLE IF NOT EXISTS stock_movements_p2024 PARTITION OF stock_movements FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
CREATE TABLE IF NOT EXISTS stock_movements_p2025 PARTITION OF stock_movements FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
CREATE TABLE IF NOT EXISTS stock_movements_p2026 PARTITION OF stock_movements FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS stock_movements_p_default PARTITION OF stock_movements DEFAULT;

CREATE SEQUENCE IF NOT EXISTS stock_movements_id_seq;
ALTER TABLE stock_movements ALTER COLUMN id SET DEFAULT nextval('stock_movements_id_seq');
ALTER SEQUENCE stock_movements_id_seq OWNED BY stock_movements.id;

DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'stock_movements_old') THEN
        PERFORM setval('stock_movements_id_seq', COALESCE((SELECT MAX(id) FROM stock_movements_old), 1));
        INSERT INTO stock_movements SELECT * FROM stock_movements_old;
        DROP TABLE stock_movements_old CASCADE;
    END IF;
END $$;

-- 3. SETUP partitioned audit_logs
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
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

CREATE TABLE IF NOT EXISTS audit_logs_p2024 PARTITION OF audit_logs FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
CREATE TABLE IF NOT EXISTS audit_logs_p2025 PARTITION OF audit_logs FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
CREATE TABLE IF NOT EXISTS audit_logs_p2026 PARTITION OF audit_logs FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS audit_logs_p_default PARTITION OF audit_logs DEFAULT;

CREATE SEQUENCE IF NOT EXISTS audit_logs_id_seq;
ALTER TABLE audit_logs ALTER COLUMN id SET DEFAULT nextval('audit_logs_id_seq');
ALTER SEQUENCE audit_logs_id_seq OWNED BY audit_logs.id;

DO $$ 
BEGIN 
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'audit_logs_old') THEN
        PERFORM setval('audit_logs_id_seq', COALESCE((SELECT MAX(id) FROM audit_logs_old), 1));
        INSERT INTO audit_logs (id, tenant_id, user_id, action, resource_type, resource_id, details, created_at) 
        SELECT id, tenant_id, user_id, action, resource_type, resource_id, details, created_at FROM audit_logs_old;
        DROP TABLE audit_logs_old CASCADE;
    END IF;
END $$;

-- 4. FINAL CONSTRAINTS AND INDEXES
-- --------------------------------------------
ALTER TABLE stock_movements ADD CONSTRAINT fk_sm_product FOREIGN KEY (product_id) REFERENCES products(id);
ALTER TABLE stock_movements ADD CONSTRAINT fk_sm_user FOREIGN KEY (created_by) REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_stock_movements_tenant ON stock_movements(tenant_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_product ON stock_movements(tenant_id, product_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_date ON audit_logs(created_at);
