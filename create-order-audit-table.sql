-- Script para crear la tabla de auditoría de órdenes (order_audit)
-- Este script debe ejecutarse en la base de datos inventory

CREATE TABLE IF NOT EXISTS order_audit (
    audit_id SERIAL PRIMARY KEY,
    order_id INTEGER,
    user_id INTEGER,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    previous_state TEXT,
    new_state TEXT,
    audit_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Keys
    CONSTRAINT fk_order_audit_order FOREIGN KEY (order_id) 
        REFERENCES order_header(order_id) ON DELETE SET NULL,
    CONSTRAINT fk_order_audit_user FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE SET NULL
);

-- Crear índices para mejorar el rendimiento
CREATE INDEX IF NOT EXISTS idx_order_audit_order ON order_audit(order_id);
CREATE INDEX IF NOT EXISTS idx_order_audit_user ON order_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_order_audit_date ON order_audit(audit_date);
CREATE INDEX IF NOT EXISTS idx_order_audit_action ON order_audit(action);

-- Comentarios para documentación
COMMENT ON TABLE order_audit IS 'Tabla de auditoría para rastrear cambios en las órdenes';
COMMENT ON COLUMN order_audit.audit_id IS 'Identificador único de la auditoría';
COMMENT ON COLUMN order_audit.order_id IS 'ID de la orden auditada (puede ser NULL si se eliminó)';
COMMENT ON COLUMN order_audit.user_id IS 'ID del usuario que realizó el cambio';
COMMENT ON COLUMN order_audit.action IS 'Tipo de acción realizada (ej: CAMBIO DE ESTADO)';
COMMENT ON COLUMN order_audit.details IS 'Descripción detallada del cambio';
COMMENT ON COLUMN order_audit.previous_state IS 'Estado anterior en formato JSON';
COMMENT ON COLUMN order_audit.new_state IS 'Nuevo estado en formato JSON';
COMMENT ON COLUMN order_audit.audit_date IS 'Fecha y hora del cambio';
