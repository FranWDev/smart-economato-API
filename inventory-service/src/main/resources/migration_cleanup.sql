-- SQL Migration to remove 'type' and 'minimumStock' columns from 'product' table
-- DROP INDEX IF EXISTS idx_product_type;
-- If the index exists, drop it first:
DROP INDEX IF EXISTS idx_product_type;

ALTER TABLE product
DROP COLUMN IF EXISTS type,
DROP COLUMN IF EXISTS minimum_stock;
