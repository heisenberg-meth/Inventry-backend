-- ============================================
-- V73__add_needed_permissions.sql
-- Add permissions that code expects but weren't in original migration
-- ============================================

INSERT INTO permissions ("key", description) VALUES
  ('view_products', 'Can view products'),
  ('create_products', 'Can create products'),
  ('update_products', 'Can update products'),
  ('delete_products', 'Can delete products'),
  ('view_inventory', 'Can view inventory'),
  ('import_products', 'Can import products'),
  ('export_products', 'Can export products'),
  ('view_customers', 'Can view customers'),
  ('create_customers', 'Can create customers'),
  ('update_customers', 'Can update customers'),
  ('delete_customers', 'Can delete customers'),
  ('view_categories', 'Can view categories'),
  ('create_categories', 'Can create categories'),
  ('update_categories', 'Can update categories'),
  ('delete_categories', 'Can delete categories'),
  ('view_suppliers', 'Can view suppliers'),
  ('create_suppliers', 'Can create suppliers'),
  ('update_suppliers', 'Can update suppliers'),
  ('delete_suppliers', 'Can delete suppliers')
ON CONFLICT ("key") DO NOTHING;