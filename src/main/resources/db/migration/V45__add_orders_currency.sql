-- Adds the `currency` column referenced by the `Order` entity. Defaults to 'INR' to match
-- `@Builder.Default` on the entity so existing rows are valid under `ddl-auto: validate`.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'INR';

-- The `Order` entity uses `@SequenceGenerator(allocationSize = 50)` for batched inserts. The
-- existing `orders_id_seq` was created with the default INCREMENT BY 1, which fails Hibernate's
-- schema-validation step. Aligning the sequence increment avoids the
-- "sequence ... defined inconsistent increment-size; found [50] but expecting [1]" startup error.
ALTER SEQUENCE orders_id_seq INCREMENT BY 50;
