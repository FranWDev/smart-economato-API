-- 1. Crear la tabla de predicciones
CREATE TABLE stock_prediction (
    product_id INTEGER PRIMARY KEY,
    projected_consumption DECIMAL(19, 4),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_stock_prediction_product 
        FOREIGN KEY (product_id) 
        REFERENCES product(product_id) 
        ON DELETE CASCADE
);

-- 2. (Opcional) Índice para mejorar el rendimiento en lecturas masivas
CREATE INDEX idx_stock_prediction_updated ON stock_prediction(updated_at);
