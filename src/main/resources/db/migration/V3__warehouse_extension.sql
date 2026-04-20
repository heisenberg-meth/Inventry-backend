-- ============================================
-- V3__warehouse_extension.sql
-- Warehouse-specific extension tables
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'warehouse_products') THEN
        CREATE TABLE warehouse_products (
  product_id        BIGINT       PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
  storage_location  VARCHAR(100),
  zone              VARCHAR(50),
  rack              VARCHAR(50),
  bin               VARCHAR(50)
);
    END IF;
END $$;

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transfer_orders') THEN
        CREATE TABLE transfer_orders (
  id              BIGSERIAL PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL REFERENCES tenants(id),
  from_location   VARCHAR(100) NOT NULL,
  to_location     VARCHAR(100) NOT NULL,
  status          VARCHAR(30)  DEFAULT 'PENDING',
  notes           TEXT,
  created_by      BIGINT       REFERENCES users(id),
  created_at      TIMESTAMP    DEFAULT NOW()
);
    END IF;
END $$;
