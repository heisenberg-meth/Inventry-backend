-- Performance indexes for optimized product queries

-- 1. Full-text search index for searchProducts
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_search') THEN
        CREATE INDEX idx_products_search ON products USING gin(to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(sku, '') || ' ' || COALESCE(barcode, '')));
    END IF;
END $$;

-- 2. Performance index for tenant-based filtering and paging
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_tenant_active') THEN
        CREATE INDEX idx_products_tenant_active ON products(tenant_id, is_active);
    END IF;
END $$;

-- 3. Performance index for stock-based filtering
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_tenant_stock') THEN
        CREATE INDEX idx_products_tenant_stock ON products(tenant_id, stock);
    END IF;
END $$;

-- 4. Paging optimization
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_tenant_id') THEN
        CREATE INDEX idx_products_tenant_id ON products(tenant_id, id);
    END IF;
END $$;

-- 5. Stock and reorder level monitoring
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_products_tenant_stock_reorder') THEN
        CREATE INDEX idx_products_tenant_stock_reorder ON products(tenant_id, stock, reorder_level) WHERE is_active = true;
    END IF;
END $$;
