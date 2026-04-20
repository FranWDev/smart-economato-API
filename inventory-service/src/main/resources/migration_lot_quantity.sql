-- Migración para añadir lot_quantity a la tabla product
ALTER TABLE product ADD COLUMN IF NOT EXISTS lot_quantity NUMERIC(10,3) DEFAULT NULL;
