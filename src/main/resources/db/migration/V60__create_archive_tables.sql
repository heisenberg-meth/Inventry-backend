-- ============================================
-- V60__create_archive_tables.sql
-- Dedicated tables for long-term storage of historical logs.
-- ============================================

CREATE TABLE IF NOT EXISTS audit_logs_archive (
    id BIGINT,
    tenant_id BIGINT,
    user_id BIGINT,
    action VARCHAR(255),
    resource_type VARCHAR(50),
    resource_id BIGINT,
    details TEXT,
    created_at TIMESTAMP,
    archived_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS stock_movements_archive (
    id BIGINT,
    tenant_id BIGINT,
    product_id BIGINT,
    movement_type VARCHAR(30),
    quantity INTEGER,
    previous_stock INTEGER,
    new_stock INTEGER,
    reference_id BIGINT,
    reference_type VARCHAR(30),
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP,
    archived_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_archive_tenant ON audit_logs_archive(tenant_id);
CREATE INDEX idx_stock_archive_tenant ON stock_movements_archive(tenant_id);
