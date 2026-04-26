-- ============================================
-- V59__tenant_purge_procedure.sql
-- Procedure for secure, multi-stage tenant data deletion (GDPR compliance)
-- ============================================

CREATE OR REPLACE PROCEDURE purge_tenant_data(p_tenant_id BIGINT)
LANGUAGE plpgsql
AS $$
BEGIN
    -- 1. Metadata and Logs
    DELETE FROM audit_logs WHERE tenant_id = p_tenant_id;
    DELETE FROM stock_movements WHERE tenant_id = p_tenant_id;
    DELETE FROM webhook_outbox WHERE tenant_id = p_tenant_id;
    DELETE FROM webhooks WHERE tenant_id = p_tenant_id;
    DELETE FROM alerts WHERE tenant_id = p_tenant_id;
    
    -- 2. Financials
    DELETE FROM invoices WHERE tenant_id = p_tenant_id;
    DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE tenant_id = p_tenant_id);
    DELETE FROM orders WHERE tenant_id = p_tenant_id;
    
    -- 3. Security
    DELETE FROM api_keys WHERE tenant_id = p_tenant_id;
    DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE tenant_id = p_tenant_id);
    DELETE FROM users WHERE tenant_id = p_tenant_id;
    
    -- 4. Core Business Data
    DELETE FROM warehouse_products WHERE product_id IN (SELECT id FROM products WHERE tenant_id = p_tenant_id);
    DELETE FROM pharmacy_products WHERE product_id IN (SELECT id FROM products WHERE tenant_id = p_tenant_id);
    DELETE FROM products WHERE tenant_id = p_tenant_id;
    DELETE FROM categories WHERE tenant_id = p_tenant_id;
    DELETE FROM suppliers WHERE tenant_id = p_tenant_id;
    DELETE FROM customers WHERE tenant_id = p_tenant_id;
    
    -- 5. Tenant Record
    DELETE FROM tenants WHERE id = p_tenant_id;
    
    RAISE NOTICE 'Tenant % data purged successfully.', p_tenant_id;
END;
$$;
