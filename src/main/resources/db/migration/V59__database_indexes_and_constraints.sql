-- V59__database_indexes_and_constraints.sql
-- Phase 15: Database Design - Additional indexes and constraints
-- 1. GIN Index for Product Full-Text Search
CREATE INDEX IF NOT EXISTS idx_products_search_gin ON products USING GIN(search_vector);
-- 2. Additional tenant_id composite indexes (if not already present)
CREATE INDEX IF NOT EXISTS idx_order_items_tenant_id ON order_items (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_tenant_id ON stock_movements (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_notifications_tenant_id ON notifications (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_alerts_tenant_id ON alerts (tenant_id, id);
-- 3. Ensure foreign key constraints exist (add if missing)
DO $$ BEGIN -- Ensure unique constraint on referenced columns for composite FK
IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ux_customers_tenant_id'
) THEN
ALTER TABLE customers
ADD CONSTRAINT ux_customers_tenant_id UNIQUE (tenant_id, id);
END IF;
IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ux_suppliers_tenant_id'
) THEN
ALTER TABLE suppliers
ADD CONSTRAINT ux_suppliers_tenant_id UNIQUE (tenant_id, id);
END IF;
-- Orders to Customers FK
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_orders_customer'
        AND table_name = 'orders'
) THEN
ALTER TABLE orders
ADD CONSTRAINT fk_orders_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id);
END IF;
-- Orders to Suppliers FK
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_orders_supplier'
        AND table_name = 'orders'
) THEN
ALTER TABLE orders
ADD CONSTRAINT fk_orders_supplier FOREIGN KEY (tenant_id, supplier_id) REFERENCES suppliers(tenant_id, id);
END IF;
-- Order Items to Orders FK
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_order_items_order'
        AND table_name = 'order_items'
) THEN
ALTER TABLE order_items
ADD CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id);
END IF;
-- Inventory to Products FK
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_inventory_product'
        AND table_name = 'inventory'
) THEN
ALTER TABLE inventory
ADD CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id);
END IF;
END $$;
-- 4. Soft delete check constraints (if not already present)
DO $$ BEGIN -- Ensure columns exist
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'products'
        AND column_name = 'is_deleted'
) THEN
ALTER TABLE products
ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
END IF;
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'customers'
        AND column_name = 'is_deleted'
) THEN
ALTER TABLE customers
ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
END IF;
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'suppliers'
        AND column_name = 'is_deleted'
) THEN
ALTER TABLE suppliers
ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
END IF;
-- Products soft delete constraint
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.check_constraints
    WHERE constraint_name = 'chk_products_is_deleted'
) THEN
ALTER TABLE products
ADD CONSTRAINT chk_products_is_deleted CHECK (is_deleted IN (TRUE, FALSE));
END IF;
-- Customers soft delete constraint
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.check_constraints
    WHERE constraint_name = 'chk_customers_is_deleted'
) THEN
ALTER TABLE customers
ADD CONSTRAINT chk_customers_is_deleted CHECK (is_deleted IN (TRUE, FALSE));
END IF;
-- Suppliers soft delete constraint
IF NOT EXISTS (
    SELECT 1
    FROM information_schema.check_constraints
    WHERE constraint_name = 'chk_suppliers_is_deleted'
) THEN
ALTER TABLE suppliers
ADD CONSTRAINT chk_suppliers_is_deleted CHECK (is_deleted IN (TRUE, FALSE));
END IF;
END $$;
-- 5. Product price positive check (if not already present)
DO $$ BEGIN IF NOT EXISTS (
    SELECT 1
    FROM information_schema.check_constraints
    WHERE constraint_name = 'chk_product_price_positive'
) THEN
ALTER TABLE products
ADD CONSTRAINT chk_product_price_positive CHECK (sale_price >= 0);
END IF;
END $$;
-- 6. Order total positive check
DO $$ BEGIN IF NOT EXISTS (
    SELECT 1
    FROM information_schema.check_constraints
    WHERE constraint_name = 'chk_order_total_positive'
) THEN
ALTER TABLE orders
ADD CONSTRAINT chk_order_total_positive CHECK (total_amount >= 0);
END IF;
END $$;
-- 7. Additional useful indexes for common queries
CREATE INDEX IF NOT EXISTS idx_orders_tenant_customer ON orders (tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_tenant_order ON invoices (tenant_id, order_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant_email ON users (tenant_id, email);
CREATE INDEX IF NOT EXISTS idx_categories_tenant_id ON categories (tenant_id, id);