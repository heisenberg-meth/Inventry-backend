-- ============================================
-- V49__create_shedlock_table.sql
-- Support for distributed scheduler locking
-- ============================================

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
