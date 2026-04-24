-- Add company_code if it doesn't exist
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS company_code VARCHAR(20);

-- Update existing tenants with a temporary code (e.g., T<id>FIX) where null
UPDATE tenants SET company_code = 'T' || id || 'FIX' WHERE company_code IS NULL;

-- Set NOT NULL
ALTER TABLE tenants ALTER COLUMN company_code SET NOT NULL;

-- Add UNIQUE constraint using an index for better cross-dialect idempotency (H2 + Postgres safe)
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenants_company_code ON tenants(company_code);
