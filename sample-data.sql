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

-- ============================================
-- PRODUCTOS
-- ============================================
INSERT INTO product (name, type, unit_of_measure, unit_price, product_code, current_stock, availability_percentage, minimum_stock, version) VALUES
-- Carnes y Proteínas
('Pollo entero', 'CARNE', 'kg', 5.99, 'CARN-001', 50.000, 95.00, 10.000, 0),
('Carne de res', 'CARNE', 'kg', 12.50, 'CARN-002', 30.500, 90.00, 5.000, 0),
('Pescado fresco', 'PESCADO', 'kg', 8.75, 'PESC-001', 25.000, 85.00, 5.000, 0),
('Camarones', 'MARISCO', 'kg', 15.00, 'MARI-001', 20.000, 80.00, 3.000, 0),

-- Vegetales
('Tomate', 'VEGETAL', 'kg', 2.50, 'VEG-001', 100.000, 90.00, 20.000, 0),
('Cebolla', 'VEGETAL', 'kg', 1.80, 'VEG-002', 80.000, 95.00, 15.000, 0),
('Zanahoria', 'VEGETAL', 'kg', 1.50, 'VEG-003', 60.000, 92.00, 10.000, 0),
('Lechuga', 'VEGETAL', 'unidad', 1.20, 'VEG-004', 50.000, 75.00, 10.000, 0),
('Pimiento rojo', 'VEGETAL', 'kg', 3.20, 'VEG-005', 40.000, 88.00, 8.000, 0),

-- Lácteos
('Leche entera', 'LACTEO', 'litro', 1.50, 'LACT-001', 150.000, 100.00, 30.000, 0),
('Queso mozzarella', 'LACTEO', 'kg', 7.50, 'LACT-002', 30.000, 98.00, 5.000, 0),
('Mantequilla', 'LACTEO', 'kg', 8.00, 'LACT-003', 25.000, 100.00, 5.000, 0),
('Crema de leche', 'LACTEO', 'litro', 3.50, 'LACT-004', 40.000, 100.00, 10.000, 0),

-- Granos y Cereales
('Arroz blanco', 'GRANO', 'kg', 2.20, 'GRAN-001', 200.000, 100.00, 40.000, 0),
('Pasta', 'GRANO', 'kg', 1.80, 'GRAN-002', 150.000, 100.00, 30.000, 0),
('Harina de trigo', 'GRANO', 'kg', 1.50, 'GRAN-003', 100.000, 100.00, 20.000, 0),
('Frijoles negros', 'GRANO', 'kg', 2.50, 'GRAN-004', 80.000, 95.00, 15.000, 0),

-- Especias y Condimentos
('Sal', 'CONDIMENTO', 'kg', 0.80, 'COND-001', 50.000, 100.00, 10.000, 0),
('Pimienta negra', 'ESPECIA', 'kg', 12.00, 'ESP-001', 10.000, 100.00, 2.000, 0),
('Aceite de oliva', 'ACEITE', 'litro', 8.50, 'ACEI-001', 60.000, 100.00, 15.000, 0),
('Vinagre', 'CONDIMENTO', 'litro', 2.00, 'COND-002', 40.000, 100.00, 10.000, 0),
('Ajo', 'CONDIMENTO', 'kg', 4.50, 'COND-003', 30.000, 85.00, 5.000, 0),

-- Frutas
('Limón', 'FRUTA', 'kg', 2.00, 'FRUT-001', 50.000, 90.00, 10.000, 0),
('Manzana', 'FRUTA', 'kg', 3.50, 'FRUT-002', 60.000, 93.00, 12.000, 0),
('Plátano', 'FRUTA', 'kg', 1.50, 'FRUT-003', 80.000, 88.00, 15.000, 0);

-- ============================================
-- ALERGENOS
-- ============================================
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

-- ============================================
-- RECETAS
-- ============================================
INSERT INTO recipe (recipe_name, elaboration, presentation, total_cost, version) VALUES
('Pasta Carbonara', 
 '1. Cocinar la pasta al dente. 2. En una sartén, dorar el bacon. 3. Mezclar huevos con queso. 4. Combinar todo fuera del fuego.',
 'Servir caliente con queso parmesano rallado y pimienta negra molida.',
 8.50, 0),

('Pollo al limón',
 '1. Sazonar el pollo con sal y pimienta. 2. Sellar en aceite caliente. 3. Agregar jugo de limón y caldo. 4. Cocinar a fuego medio 25 minutos.',
 'Servir con arroz blanco y verduras al vapor.',
 12.30, 0),

('Ensalada César',
 '1. Lavar y cortar la lechuga. 2. Preparar aderezo con ajo, limón y aceite. 3. Tostar el pan para crutones. 4. Mezclar todo.',
 'Servir en plato frío con queso parmesano en escamas.',
 6.75, 0),

('Arroz con camarones',
 '1. Sofreír cebolla y pimiento. 2. Agregar arroz y tostar. 3. Añadir caldo y cocinar. 4. Incorporar camarones al final.',
 'Decorar con perejil fresco y rodajas de limón.',
 18.90, 0),

('Sopa de vegetales',
 '1. Picar todos los vegetales. 2. Sofreír cebolla y ajo. 3. Agregar vegetales y caldo. 4. Cocinar 20 minutos.',
 'Servir caliente con pan tostado.',
 5.40, 0);

-- ============================================
-- COMPONENTES DE RECETAS
-- ============================================
-- Pasta Carbonara (recipe_id = 1)
INSERT INTO recipe_component (parent_recipe_id, product_id, quantity) VALUES
(1, 15, 0.400), -- Pasta 400g
(1, 11, 0.200), -- Queso mozzarella 200g
(1, 12, 0.050), -- Mantequilla 50g
(1, 18, 0.010), -- Sal 10g
(1, 19, 0.005); -- Pimienta 5g

-- Pollo al limón (recipe_id = 2)
INSERT INTO recipe_component (parent_recipe_id, product_id, quantity) VALUES
(2, 1, 0.600),  -- Pollo 600g
(2, 23, 0.100), -- Limón 100g
(2, 20, 0.050), -- Aceite de oliva 50ml
(2, 14, 0.200), -- Arroz 200g
(2, 22, 0.020); -- Ajo 20g

-- Ensalada César (recipe_id = 3)
INSERT INTO recipe_component (parent_recipe_id, product_id, quantity) VALUES
(3, 8, 2.000),  -- Lechuga 2 unidades
(3, 11, 0.050), -- Queso 50g
(3, 20, 0.030), -- Aceite de oliva 30ml
(3, 22, 0.010), -- Ajo 10g
(3, 23, 0.050); -- Limón 50g

-- Arroz con camarones (recipe_id = 4)
INSERT INTO recipe_component (parent_recipe_id, product_id, quantity) VALUES
(4, 14, 0.300), -- Arroz 300g
(4, 4, 0.400),  -- Camarones 400g
(4, 5, 0.200),  -- Tomate 200g
(4, 6, 0.150),  -- Cebolla 150g
(4, 9, 0.100),  -- Pimiento 100g
(4, 22, 0.020); -- Ajo 20g

-- Sopa de vegetales (recipe_id = 5)
INSERT INTO recipe_component (parent_recipe_id, product_id, quantity) VALUES
(5, 5, 0.200),  -- Tomate 200g
(5, 6, 0.150),  -- Cebolla 150g
(5, 7, 0.200),  -- Zanahoria 200g
(5, 9, 0.100),  -- Pimiento 100g
(5, 22, 0.030); -- Ajo 30g

-- ============================================
-- ALERGENOS DE RECETAS
-- ============================================
-- Pasta Carbonara - contiene gluten y lácteos
INSERT INTO recipe_allergen (recipe_id, allergen_id) VALUES
(1, 1), -- Gluten
(1, 2); -- Lácteos

-- Pollo al limón - no tiene alérgenos principales en esta receta simple

-- Ensalada César - contiene lácteos
INSERT INTO recipe_allergen (recipe_id, allergen_id) VALUES
(3, 2); -- Lácteos

-- Arroz con camarones - contiene mariscos
INSERT INTO recipe_allergen (recipe_id, allergen_id) VALUES
(4, 5); -- Mariscos

-- Sopa de vegetales - puede contener apio
INSERT INTO recipe_allergen (recipe_id, allergen_id) VALUES
(5, 9); -- Apio

-- ============================================
-- PEDIDOS
-- ============================================
INSERT INTO order_header (user_id, order_date, status, version) VALUES
(1, '2026-01-15 10:30:00', 'CONFIRMED', 0),
(2, '2026-01-20 14:15:00', 'PENDING', 0),
(3, '2026-01-25 09:00:00', 'IN_REVIEW', 0),
(4, '2026-01-28 16:45:00', 'CREATED', 0),
(1, '2026-01-30 11:20:00', 'CONFIRMED', 0);

-- ============================================
-- DETALLES DE PEDIDOS
-- ============================================
-- Pedido 1
INSERT INTO order_detail (order_id, product_id, requested_quantity, received_quantity) VALUES
(1, 1, 10.000, 10.000),  -- Pollo 10kg
(1, 14, 20.000, 20.000), -- Arroz 20kg
(1, 5, 15.000, 15.000);  -- Tomate 15kg

-- Pedido 2
INSERT INTO order_detail (order_id, product_id, requested_quantity, received_quantity) VALUES
(2, 3, 5.000, 4.800),    -- Pescado 5kg (recibido 4.8kg)
(2, 4, 3.000, 3.000),    -- Camarones 3kg
(2, 10, 10.000, NULL);   -- Leche 10L (no recibido aún)

-- Pedido 3
INSERT INTO order_detail (order_id, product_id, requested_quantity, received_quantity) VALUES
(3, 15, 25.000, NULL),   -- Pasta 25kg
(3, 11, 8.000, NULL),    -- Queso 8kg
(3, 20, 5.000, NULL);    -- Aceite 5L

-- Pedido 4
INSERT INTO order_detail (order_id, product_id, requested_quantity, received_quantity) VALUES
(4, 6, 20.000, NULL),    -- Cebolla 20kg
(4, 7, 15.000, NULL),    -- Zanahoria 15kg
(4, 8, 30.000, NULL);    -- Lechuga 30 unidades

-- Pedido 5
INSERT INTO order_detail (order_id, product_id, requested_quantity, received_quantity) VALUES
(5, 2, 12.000, 12.000),  -- Carne de res 12kg
(5, 16, 10.000, 10.000), -- Harina 10kg
(5, 12, 5.000, 5.000);   -- Mantequilla 5kg

-- ============================================
-- MOVIMIENTOS DE INVENTARIO (AUDITORÍA)
-- ============================================
INSERT INTO inventory_audit (product_id, user_id, movement_type, quantity, date, previous_state, new_state) VALUES
-- Entradas de inventario
(1, 1, 'ENTRADA', 50.000, '2026-01-15 10:45:00', 
 '{"stock": 0.0, "price": 5.99}', 
 '{"stock": 50.0, "price": 5.99}'),

(14, 1, 'ENTRADA', 200.000, '2026-01-15 11:00:00',
 '{"stock": 0.0, "price": 2.20}',
 '{"stock": 200.0, "price": 2.20}'),

-- Salidas por consumo
(1, 2, 'SALIDA', -10.000, '2026-01-20 15:30:00',
 '{"stock": 50.0, "price": 5.99}',
 '{"stock": 40.0, "price": 5.99}'),

(14, 2, 'SALIDA', -5.000, '2026-01-20 15:35:00',
 '{"stock": 200.0, "price": 2.20}',
 '{"stock": 195.0, "price": 2.20}'),

-- Ajustes de inventario
(3, 3, 'AJUSTE', -0.200, '2026-01-25 10:00:00',
 '{"stock": 25.0, "price": 8.75, "reason": "Merma"}',
 '{"stock": 24.8, "price": 8.75, "reason": "Merma por deterioro"}'),

-- Devoluciones
(4, 1, 'DEVOLUCION', 2.000, '2026-01-28 09:15:00',
 '{"stock": 18.0, "price": 15.00}',
 '{"stock": 20.0, "price": 15.00}');

-- ============================================
-- AUDITORÍA DE RECETAS
-- ============================================
INSERT INTO recipe_audit (recipe_id, user_id, action_type, action_date, previous_state, new_state) VALUES
(1, 2, 'MODIFICACION', '2026-01-18 14:30:00',
 '{"total_cost": 8.00, "components": 4}',
 '{"total_cost": 8.50, "components": 5, "change": "Añadido pimienta negra"}'),

(2, 2, 'CREACION', '2026-01-10 10:00:00',
 NULL,
 '{"recipe_name": "Pollo al limón", "total_cost": 12.30}'),

(4, 3, 'MODIFICACION', '2026-01-22 16:20:00',
 '{"elaboration": "Preparación simple"}',
 '{"elaboration": "Preparación detallada con tiempos", "updated_by": "Maria Manager"}');

-- ============================================
-- Resumen
-- ============================================
SELECT 'Datos insertados correctamente:' AS mensaje;
SELECT '- Usuarios: ' || COUNT(*) FROM users;
SELECT '- Productos: ' || COUNT(*) FROM product;
SELECT '- Alérgenos: ' || COUNT(*) FROM allergen;
SELECT '- Recetas: ' || COUNT(*) FROM recipe;
SELECT '- Componentes de recetas: ' || COUNT(*) FROM recipe_component;
SELECT '- Pedidos: ' || COUNT(*) FROM order_header;
SELECT '- Detalles de pedidos: ' || COUNT(*) FROM order_detail;
SELECT '- Movimientos de inventario: ' || COUNT(*) FROM inventory_audit;
SELECT '- Auditoría de recetas: ' || COUNT(*) FROM recipe_audit;
