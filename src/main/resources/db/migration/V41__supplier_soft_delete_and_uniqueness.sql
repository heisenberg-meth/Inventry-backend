-- Add is_deleted column to suppliers for soft-delete support
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

-- Create unique indexes for email and phone per tenant, excluding deleted records
-- This aligns with PRD Section 9.3
CREATE UNIQUE INDEX IF NOT EXISTS ux_suppliers_tenant_email 
ON suppliers (tenant_id, email) 
WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_suppliers_tenant_phone 
ON suppliers (tenant_id, phone) 
WHERE is_deleted = false;

-- Add updated_at for better tracking
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
