-- Adds the `currency` columns referenced by the `Order` and `Payment` entities. Both default
-- to 'INR' to match `@Builder.Default` on the entities so existing rows are valid under
-- `ddl-auto: validate`. Without these columns, application startup fails with:
--   Schema-validation: missing column [currency] in table [orders|payments]
ALTER TABLE orders ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'INR';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'INR';
