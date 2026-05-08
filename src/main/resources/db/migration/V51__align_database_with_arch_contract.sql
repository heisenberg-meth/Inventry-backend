-- V51__align_database_with_arch_contract.sql
-- Aligning schema with Section 15 Architecture Contract
-- 1. Create inventory table (Section 15.4.6)
CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    low_stock_threshold INT,
    reorder_level INT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
-- Constraints for inventory
ALTER TABLE inventory
ADD CONSTRAINT chk_inventory_quantity CHECK (
        quantity >= 0
        AND reserved_quantity >= 0
        AND reserved_quantity <= quantity
    );
CREATE INDEX idx_inventory_tenant_id ON inventory (tenant_id, id);
CREATE INDEX idx_inventory_product_id ON inventory (tenant_id, product_id);
-- 2. Migrate data from products to inventory
INSERT INTO inventory (
        tenant_id,
        product_id,
        quantity,
        reorder_level,
        created_at,
        updated_at
    )
SELECT tenant_id,
    id,
    stock,
    reorder_level,
    created_at,
    updated_at
FROM products;
-- 3. Remove redundant columns from products
ALTER TABLE products DROP COLUMN stock;
ALTER TABLE products DROP COLUMN reorder_level;
-- 4. Create user_roles join table (Section 15.4.4)
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
-- Migrate existing roles from users table (assuming role string matches role name)
-- This is a best-effort migration
INSERT INTO user_roles (user_id, role_id)
SELECT u.id,
    r.id
FROM users u
    JOIN roles r ON u.role = r.name;
-- 5. Refine audit_logs (Section 15.4.11)
ALTER TABLE audit_logs
    RENAME COLUMN old_value TO before_value;
ALTER TABLE audit_logs
    RENAME COLUMN new_value TO after_value;
ALTER TABLE audit_logs
ADD COLUMN request_id TEXT;
-- 6. Ensure Tenant + ID Indexes (Section 15.6.1)
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_id ON orders (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_invoices_tenant_id ON invoices (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_customers_tenant_id ON customers (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_categories_tenant_id ON categories (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_suppliers_tenant_id ON suppliers (tenant_id, id);
-- 7. Soft Delete Rule Consistency (Section 15.3.4)
-- Most tables already have is_deleted, ensuring it exists for core ones
ALTER TABLE products
ALTER COLUMN is_deleted
SET DEFAULT FALSE;
ALTER TABLE customers
ALTER COLUMN is_deleted
SET DEFAULT FALSE;
-- 8. Unique index for products (Section 15.4.5)
DROP INDEX IF EXISTS idx_products_sku;
CREATE UNIQUE INDEX ux_products_tenant_sku ON products (tenant_id, sku)
WHERE is_deleted = false;