-- ============================================
-- V1__create_core_tables.sql
-- Core tables for IMS multi-tenant system
-- ============================================

-- TENANTS
CREATE TABLE IF NOT EXISTS tenants (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(255) NOT NULL,
  domain        VARCHAR(255) UNIQUE,
  business_type VARCHAR(50)  NOT NULL,
  plan          VARCHAR(50)  DEFAULT 'FREE',
  status        VARCHAR(20)  DEFAULT 'ACTIVE',
  created_at    TIMESTAMP    DEFAULT NOW()
);

-- USERS
CREATE TABLE IF NOT EXISTS users (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT       REFERENCES tenants(id),
  name          VARCHAR(255) NOT NULL,
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          VARCHAR(50)  NOT NULL,
  is_active     BOOLEAN      DEFAULT TRUE,
  created_at    TIMESTAMP    DEFAULT NOW()
);

-- CATEGORIES
CREATE TABLE IF NOT EXISTS categories (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
  name          VARCHAR(255) NOT NULL,
  description   TEXT,
  created_at    TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_categories_tenant ON categories(tenant_id);

-- PRODUCTS (Core)
CREATE TABLE IF NOT EXISTS products (
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

CREATE INDEX IF NOT EXISTS idx_products_tenant  ON products(tenant_id);
CREATE INDEX IF NOT EXISTS idx_products_sku     ON products(tenant_id, sku);
CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(tenant_id, barcode);

-- SUPPLIERS
CREATE TABLE IF NOT EXISTS suppliers (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
  name          VARCHAR(255) NOT NULL,
  phone         VARCHAR(50),
  email         VARCHAR(255),
  address       TEXT,
  gstin         VARCHAR(20),
  created_at    TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_suppliers_tenant ON suppliers(tenant_id);

-- CUSTOMERS
CREATE TABLE IF NOT EXISTS customers (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
  name          VARCHAR(255) NOT NULL,
  phone         VARCHAR(50),
  email         VARCHAR(255),
  address       TEXT,
  created_at    TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customers_tenant ON customers(tenant_id);

-- ORDERS
CREATE TABLE IF NOT EXISTS orders (
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

CREATE INDEX IF NOT EXISTS idx_orders_tenant  ON orders(tenant_id);
CREATE INDEX IF NOT EXISTS idx_orders_type    ON orders(tenant_id, type);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(tenant_id, created_at);

-- ORDER ITEMS
CREATE TABLE IF NOT EXISTS order_items (
  id            BIGSERIAL PRIMARY KEY,
  order_id      BIGINT        NOT NULL REFERENCES orders(id),
  product_id    BIGINT        NOT NULL REFERENCES products(id),
  quantity      INTEGER       NOT NULL,
  unit_price    DECIMAL(10,2) NOT NULL,
  discount      DECIMAL(10,2) DEFAULT 0,
  tax_rate      DECIMAL(5,2)  DEFAULT 0,
  total         DECIMAL(10,2) NOT NULL
);

-- INVOICES
CREATE TABLE IF NOT EXISTS invoices (
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

CREATE INDEX IF NOT EXISTS idx_invoices_tenant ON invoices(tenant_id);

-- STOCK MOVEMENTS (Audit Trail)
CREATE TABLE IF NOT EXISTS stock_movements (
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

CREATE INDEX IF NOT EXISTS idx_stock_movements_tenant  ON stock_movements(tenant_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_product ON stock_movements(tenant_id, product_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_date    ON stock_movements(tenant_id, created_at);
