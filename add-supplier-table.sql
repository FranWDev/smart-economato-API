-- Script para crear la tabla supplier y añadir la relación con products

-- Crear tabla supplier
CREATE TABLE IF NOT EXISTS supplier (
    supplier_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    INDEX idx_supplier_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Añadir columna supplier_id a la tabla product
ALTER TABLE product
ADD COLUMN supplier_id INT NULL,
ADD CONSTRAINT fk_product_supplier 
    FOREIGN KEY (supplier_id) 
    REFERENCES supplier(supplier_id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE;

-- Crear índice en supplier_id para mejorar el rendimiento
CREATE INDEX idx_product_supplier ON product(supplier_id);

-- Insertar algunos proveedores de ejemplo (opcional)
INSERT INTO supplier (name) VALUES 
    ('Distribuidora Nacional S.A.'),
    ('Importaciones Gourmet'),
    ('Productos del Campo'),
    ('Carnes Premium');

-- Actualizar productos existentes con un proveedor por defecto (opcional)
-- Descomenta estas líneas si quieres asignar el primer proveedor a todos los productos existentes
-- UPDATE product SET supplier_id = 1 WHERE supplier_id IS NULL;
