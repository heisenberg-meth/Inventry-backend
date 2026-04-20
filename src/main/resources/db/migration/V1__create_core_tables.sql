-- ============================================
-- V1__create_core_tables.sql
-- Core tables for IMS multi-tenant system
-- ============================================

DO $$ 
BEGIN 
    -- TENANTS
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'tenants') THEN
        CREATE TABLE tenants (
          id            BIGSERIAL PRIMARY KEY,
          name          VARCHAR(255) NOT NULL,
          domain        VARCHAR(255) UNIQUE,
          business_type VARCHAR(50)  NOT NULL,
          plan          VARCHAR(50)  DEFAULT 'FREE',
          status        VARCHAR(20)  DEFAULT 'ACTIVE',
          created_at    TIMESTAMP    DEFAULT NOW()
        );
    END IF;

    -- USERS
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'users') THEN
        CREATE TABLE users (
          id            BIGSERIAL PRIMARY KEY,
          tenant_id     BIGINT       REFERENCES tenants(id),
          name          VARCHAR(255) NOT NULL,
          email         VARCHAR(255) UNIQUE NOT NULL,
          password_hash VARCHAR(255) NOT NULL,
          role          VARCHAR(50)  NOT NULL,
          is_active     BOOLEAN      DEFAULT TRUE,
          created_at    TIMESTAMP    DEFAULT NOW()
        );
    END IF;

    -- CATEGORIES
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'categories') THEN
        CREATE TABLE categories (
          id            BIGSERIAL PRIMARY KEY,
          tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
          name          VARCHAR(255) NOT NULL,
          description   TEXT,
          created_at    TIMESTAMP    DEFAULT NOW()
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_categories_tenant') THEN
        CREATE INDEX idx_categories_tenant ON categories(tenant_id);
    END IF;

    -- PRODUCTS (Core)
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'products') THEN
        CREATE TABLE products (
          id              BIGSERIAL PRIMARY KEY,
          tenant_id       BIGINT        NOT NULL REFERENCES tenants(id),
          name            VARCHAR(255)  NOT NULL,
          sku             VARCHAR(100),
          barcode         VARCHAR(100),
          category_id     BIGINT        REFERENCES categories(id),
          unit            VARCHAR(50),
          purchase_price  DECIMAL(10,2),
          sale_price      DECIMAL(10,2) NOT NULL,
          stock           INTEGER       DEFAULT 0,
          reorder_level   INTEGER       DEFAULT 10,
          is_active       BOOLEAN       DEFAULT TRUE,
          created_at      TIMESTAMP     DEFAULT NOW(),
          updated_at      TIMESTAMP     DEFAULT NOW()
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_tenant') THEN
        CREATE INDEX idx_products_tenant  ON products(tenant_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_sku') THEN
        CREATE INDEX idx_products_sku     ON products(tenant_id, sku);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_barcode') THEN
        CREATE INDEX idx_products_barcode ON products(tenant_id, barcode);
    END IF;

    -- SUPPLIERS
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'suppliers') THEN
        CREATE TABLE suppliers (
          id            BIGSERIAL PRIMARY KEY,
          tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
          name          VARCHAR(255) NOT NULL,
          phone         VARCHAR(50),
          email         VARCHAR(255),
          address       TEXT,
          gstin         VARCHAR(20),
          created_at    TIMESTAMP    DEFAULT NOW()
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_suppliers_tenant') THEN
        CREATE INDEX idx_suppliers_tenant ON suppliers(tenant_id);
    END IF;

    -- CUSTOMERS
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'customers') THEN
        CREATE TABLE customers (
          id            BIGSERIAL PRIMARY KEY,
          tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
          name          VARCHAR(255) NOT NULL,
          phone         VARCHAR(50),
          email         VARCHAR(255),
          address       TEXT,
          created_at    TIMESTAMP    DEFAULT NOW()
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customers_tenant') THEN
        CREATE INDEX idx_customers_tenant ON customers(tenant_id);
    END IF;

    -- ORDERS
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'orders') THEN
        CREATE TABLE orders (
          id            BIGSERIAL PRIMARY KEY,
          tenant_id     BIGINT        NOT NULL REFERENCES tenants(id),
          type          VARCHAR(20)   NOT NULL,
          status        VARCHAR(30)   DEFAULT 'PENDING',
          customer_id   BIGINT        REFERENCES customers(id),
          supplier_id   BIGINT        REFERENCES suppliers(id),
          total_amount  DECIMAL(12,2),
          tax_amount    DECIMAL(12,2),
          discount      DECIMAL(12,2) DEFAULT 0,
          notes         TEXT,
          created_by    BIGINT        REFERENCES users(id),
          created_at    TIMESTAMP     DEFAULT NOW()
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_orders_tenant') THEN
        CREATE INDEX idx_orders_tenant  ON orders(tenant_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_orders_type') THEN
        CREATE INDEX idx_orders_type    ON orders(tenant_id, type);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_orders_created') THEN
        CREATE INDEX idx_orders_created ON orders(tenant_id, created_at);
    END IF;

    -- ORDER ITEMS
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'order_items') THEN
        CREATE TABLE order_items (
          id            BIGSERIAL PRIMARY KEY,
          order_id      BIGINT        NOT NULL REFERENCES orders(id),
          product_id    BIGINT        NOT NULL REFERENCES products(id),
          quantity      INTEGER       NOT NULL,
          unit_price    DECIMAL(10,2) NOT NULL,
          discount      DECIMAL(10,2) DEFAULT 0,
          tax_rate      DECIMAL(5,2)  DEFAULT 0,
          total         DECIMAL(10,2) NOT NULL
        );
    END IF;

    -- INVOICES
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'invoices') THEN
        CREATE TABLE invoices (
          id              BIGSERIAL PRIMARY KEY,
          tenant_id       BIGINT        NOT NULL REFERENCES tenants(id),
          order_id        BIGINT        REFERENCES orders(id),
          invoice_number  VARCHAR(50)   NOT NULL,
          amount          DECIMAL(12,2) NOT NULL,
          tax_amount      DECIMAL(12,2),
          status          VARCHAR(20)   DEFAULT 'UNPAID',
          due_date        DATE,
          paid_at         TIMESTAMP,
          created_at      TIMESTAMP     DEFAULT NOW()
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_invoices_tenant') THEN
        CREATE INDEX idx_invoices_tenant ON invoices(tenant_id);
    END IF;

    -- STOCK MOVEMENTS (Audit Trail)
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'stock_movements') THEN
        CREATE TABLE stock_movements (
          id              BIGSERIAL PRIMARY KEY,
          tenant_id       BIGINT      NOT NULL,
          product_id      BIGINT      NOT NULL REFERENCES products(id),
          movement_type   VARCHAR(30) NOT NULL,
          quantity        INTEGER     NOT NULL,
          previous_stock  INTEGER,
          new_stock       INTEGER,
          reference_id    BIGINT,
          reference_type  VARCHAR(30),
          notes           TEXT,
          created_by      BIGINT      REFERENCES users(id),
          created_at      TIMESTAMP   DEFAULT NOW()
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_stock_movements_tenant') THEN
        CREATE INDEX idx_stock_movements_tenant  ON stock_movements(tenant_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_stock_movements_product') THEN
        CREATE INDEX idx_stock_movements_product ON stock_movements(tenant_id, product_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_stock_movements_date') THEN
        CREATE INDEX idx_stock_movements_date    ON stock_movements(tenant_id, created_at);
    END IF;
END $$;
