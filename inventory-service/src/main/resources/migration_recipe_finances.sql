-- Añadir columna de precio de venta a las recetas.
ALTER TABLE recipe ADD COLUMN IF NOT EXISTS selling_price DECIMAL(10, 2);

-- Inicializar el precio de venta con un margen del 20% sobre el coste total (ya incluye merma).
UPDATE recipe SET selling_price = total_cost * 1.20 WHERE selling_price IS NULL;
