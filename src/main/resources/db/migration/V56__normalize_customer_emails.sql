-- V56__normalize_customer_emails.sql
-- Normalize existing customer emails to lowercase for consistency
UPDATE customers
SET email = LOWER(TRIM(email))
WHERE email IS NOT NULL;
-- Also normalize supplier emails
UPDATE suppliers
SET email = LOWER(TRIM(email))
WHERE email IS NOT NULL;