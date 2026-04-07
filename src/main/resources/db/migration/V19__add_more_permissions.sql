-- Add missing permissions for categories and suppliers
INSERT INTO permissions ("key", description) VALUES
  ('create_category', 'Can create product categories'),
  ('update_category', 'Can update product categories'),
  ('delete_category', 'Can delete product categories'),
  ('create_supplier', 'Can manage suppliers'),
  ('update_supplier', 'Can update supplier details'),
  ('delete_supplier', 'Can delete suppliers');
