CREATE TABLE IF NOT EXISTS system_config (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    presence_audit_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    presence_auto_cleanup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    presence_auto_cleanup_days INTEGER,
    alert_threshold_ok_days INTEGER NOT NULL DEFAULT 21,
    alert_threshold_low_days INTEGER NOT NULL DEFAULT 14,
    alert_threshold_medium_days INTEGER NOT NULL DEFAULT 7,
    alert_threshold_high_days INTEGER NOT NULL DEFAULT 3,
    expiration_critical_days INTEGER NOT NULL DEFAULT 3,
    expiration_high_days INTEGER NOT NULL DEFAULT 7,
    expiration_medium_days INTEGER NOT NULL DEFAULT 14,
    forecast_horizon_days INTEGER NOT NULL DEFAULT 14,
    forecast_history_weeks INTEGER NOT NULL DEFAULT 12,
    prediction_refresh_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    prediction_refresh_interval_hours INTEGER NOT NULL DEFAULT 6,
    prediction_history_days INTEGER NOT NULL DEFAULT 90,
    prediction_batch_size INTEGER NOT NULL DEFAULT 20,
    stale_session_timeout_seconds BIGINT NOT NULL DEFAULT 60,
    jwt_expiration_ms BIGINT NOT NULL DEFAULT 86400000,
    min_password_length INTEGER NOT NULL DEFAULT 6,
    max_escalation_minutes INTEGER NOT NULL DEFAULT 1440,
    max_chat_message_length INTEGER NOT NULL DEFAULT 5000,
    max_admin_attachable_audits INTEGER NOT NULL DEFAULT 200,
    max_upload_file_size_bytes BIGINT NOT NULL DEFAULT 10485760,
    allowed_file_types VARCHAR(1000) NOT NULL DEFAULT 'image/jpeg,image/png,image/gif,image/webp,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    notify_weekly_plan_created BOOLEAN NOT NULL DEFAULT TRUE,
    notify_weekly_plan_activated BOOLEAN NOT NULL DEFAULT TRUE,
    notify_weekly_plan_slot_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
    notify_weekly_plan_day_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
    notify_weekly_plan_completed BOOLEAN NOT NULL DEFAULT TRUE,
    notify_weekly_plan_cancelled BOOLEAN NOT NULL DEFAULT TRUE,
    notify_food_crisis_activated BOOLEAN NOT NULL DEFAULT TRUE,
    notify_food_crisis_lifted BOOLEAN NOT NULL DEFAULT TRUE,
    notify_stock_prediction_triggered BOOLEAN NOT NULL DEFAULT TRUE,
    notify_incident_created BOOLEAN NOT NULL DEFAULT TRUE,
    notify_incident_opened BOOLEAN NOT NULL DEFAULT TRUE,
    notify_incident_closed BOOLEAN NOT NULL DEFAULT TRUE,
    notify_incident_chat_message BOOLEAN NOT NULL DEFAULT TRUE,
    notification_retention_days INTEGER,
    notification_auto_cleanup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    outbox_processing_interval_ms BIGINT NOT NULL DEFAULT 5000,
    outbox_batch_size INTEGER NOT NULL DEFAULT 50,
    outbox_max_consecutive_failures INTEGER NOT NULL DEFAULT 3,
    kafka_send_timeout_seconds INTEGER NOT NULL DEFAULT 5,
    updated_at TIMESTAMP,
    updated_by INTEGER REFERENCES users(user_id)
);

INSERT INTO system_config (id)
SELECT 1
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE id = 1);

CREATE TABLE IF NOT EXISTS valid_unit (
    id SERIAL PRIMARY KEY,
    code VARCHAR(30) UNIQUE NOT NULL,
    category VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO valid_unit (code, category, active) VALUES
('KG','PESO',TRUE),('G','PESO',TRUE),('MG','PESO',TRUE),('ONZA','PESO',TRUE),('LIBRA','PESO',TRUE),
('L','VOLUMEN',TRUE),('ML','VOLUMEN',TRUE),('CL','VOLUMEN',TRUE),('DL','VOLUMEN',TRUE),('GARRAFA','VOLUMEN',TRUE),
('CUCHARADA','COCINA',TRUE),('CUCHARADITA','COCINA',TRUE),('TAZA','COCINA',TRUE),('PIZCA','COCINA',TRUE),('VASO','COCINA',TRUE),
('UNIDAD','DISCRETA',TRUE),('UND','DISCRETA',TRUE),('UDS','DISCRETA',TRUE),('PIEZA','DISCRETA',TRUE),('DOCENA','DISCRETA',TRUE),
('BOTE','ENVASE',TRUE),('LATA','ENVASE',TRUE),('PAQUETE','ENVASE',TRUE),('SOBRE','ENVASE',TRUE),('BOLSA','ENVASE',TRUE),
('CAJA','ENVASE',TRUE),('SACO','ENVASE',TRUE),('BANDEJA','ENVASE',TRUE),('TUBO','ENVASE',TRUE),
('MANOJO','FORMA',TRUE),('HOJA','FORMA',TRUE),('LONCHA','FORMA',TRUE),('DIENTE','FORMA',TRUE),('RAMA','FORMA',TRUE),
('FILETE','FORMA',TRUE),('RODAJA','FORMA',TRUE),('REBANADA','FORMA',TRUE)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS system_config_audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(user_id),
    category VARCHAR(30) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    changed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_system_config_audit_log_category_changed_at
    ON system_config_audit_log (category, changed_at DESC);
