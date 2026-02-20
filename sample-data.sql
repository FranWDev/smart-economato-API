-- ============================================
-- Script de datos de ejemplo para inventory
-- ============================================

-- Limpiar datos existentes (en orden inverso por FK)
DELETE FROM order_detail;
DELETE FROM order_header;
DELETE FROM recipe_component;
DELETE FROM recipe_allergen;
DELETE FROM recipe;
DELETE FROM inventory_audit;
DELETE FROM product;
DELETE FROM allergen;
DELETE FROM users;

-- Reiniciar secuencias
ALTER SEQUENCE users_user_id_seq RESTART WITH 1;
ALTER SEQUENCE product_product_id_seq RESTART WITH 1;
ALTER SEQUENCE allergen_allergen_id_seq RESTART WITH 1;
ALTER SEQUENCE recipe_recipe_id_seq RESTART WITH 1;
ALTER SEQUENCE recipe_component_id_seq RESTART WITH 1;
ALTER SEQUENCE order_header_order_id_seq RESTART WITH 1;
ALTER SEQUENCE inventory_audit_id_seq RESTART WITH 1;

-- ============================================
-- USUARIOS
-- ============================================
INSERT INTO users (name, email, password, role) VALUES
('Admin', 'admin@economatom.com', '$2a$12$0qoUFZjOG52YNQ9aMH5xtuMIhXxe5IVXdkq5UvQKRunz5WIvy23s.', 'ADMIN'), -- password: admin123
('Chef', 'chef@economatom.com', '$2a$12$0qoUFZjOG52YNQ9aMH5xtuMIhXxe5IVXdkq5UvQKRunz5WIvy23s.', 'CHEF'), -- password: admin123
('Usuario', 'user@economatom.com', '$2a$12$0qoUFZjOG52YNQ9aMH5xtuMIhXxe5IVXdkq5UvQKRunz5WIvy23s.', 'USER'); -- password: admin123


INSERT INTO allergen (name) VALUES
('Gluten'),
('Lácteos'),
('Huevo'),
('Pescado'),
('Mariscos'),
('Frutos secos'),
('Soja'),
('Mostaza'),
('Apio'),
('Sulfitos');