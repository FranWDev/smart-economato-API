-- 1. Añadimos las columnas (primero como nullables para facilitar la carga de datos)
ALTER TABLE product 
    ADD COLUMN availability_percentage NUMERIC(5, 2),
    ADD COLUMN minimum_stock NUMERIC(10, 3);

-- 2. Actualizamos los registros existentes con valores aleatorios
-- random() genera un valor entre 0.0 y 1.0
UPDATE product 
SET 
    -- Fórmula: (random() * (max - min) + min)
    minimum_stock = ROUND((random() * 20)::numeric, 3),
    availability_percentage = ROUND((random() * (100 - 70) + 70)::numeric, 2);

-- 3. Aplicamos la restricción NOT NULL a minimum_stock una vez llenos los datos
ALTER TABLE product 
    ALTER COLUMN minimum_stock SET NOT NULL;

-- 4. Establecemos el valor por defecto para futuras inserciones (opcional)
ALTER TABLE product ALTER COLUMN availability_percentage SET DEFAULT 100.00;
ALTER TABLE product ALTER COLUMN minimum_stock SET DEFAULT 0.000;