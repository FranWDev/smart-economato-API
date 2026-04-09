-- Manual migration script for incident module tables
-- Note: project currently does not use Flyway/Liquibase in inventory-service.

CREATE TABLE IF NOT EXISTS incident_type (
    incident_type_id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS incident (
    incident_id BIGSERIAL PRIMARY KEY,
    incident_type_id INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    severity VARCHAR(20),
    created_by INTEGER NOT NULL,
    related_teacher_id INTEGER,
    resolution TEXT,
    opened_at TIMESTAMP,
    opened_by INTEGER,
    closed_at TIMESTAMP,
    closed_by INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_incident_type FOREIGN KEY (incident_type_id) REFERENCES incident_type(incident_type_id),
    CONSTRAINT fk_incident_created_by FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT fk_incident_related_teacher FOREIGN KEY (related_teacher_id) REFERENCES users(user_id),
    CONSTRAINT fk_incident_opened_by FOREIGN KEY (opened_by) REFERENCES users(user_id),
    CONSTRAINT fk_incident_closed_by FOREIGN KEY (closed_by) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_incident_status ON incident(status);
CREATE INDEX IF NOT EXISTS idx_incident_severity ON incident(severity);
CREATE INDEX IF NOT EXISTS idx_incident_created_by ON incident(created_by);
CREATE INDEX IF NOT EXISTS idx_incident_created_at ON incident(created_at);

CREATE TABLE IF NOT EXISTS incident_audit_attachment (
    incident_audit_attachment_id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    cooking_audit_id BIGINT NOT NULL,
    attached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attached_by INTEGER NOT NULL,
    reverted BOOLEAN NOT NULL DEFAULT FALSE,
    reverted_at TIMESTAMP,
    reverted_by INTEGER,
    CONSTRAINT fk_incident_attachment_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id),
    CONSTRAINT fk_incident_attachment_cooking_audit FOREIGN KEY (cooking_audit_id) REFERENCES recipe_cooking_audit(cooking_audit_id),
    CONSTRAINT fk_incident_attachment_attached_by FOREIGN KEY (attached_by) REFERENCES users(user_id),
    CONSTRAINT fk_incident_attachment_reverted_by FOREIGN KEY (reverted_by) REFERENCES users(user_id),
    CONSTRAINT uk_incident_audit_attachment UNIQUE (incident_id, cooking_audit_id)
);

CREATE TABLE IF NOT EXISTS incident_chat_message (
    incident_chat_message_id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    author_id INTEGER NOT NULL,
    content TEXT,
    has_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    attachment_url VARCHAR(500),
    attachment_filename VARCHAR(255),
    attachment_content_type VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_chat_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id),
    CONSTRAINT fk_incident_chat_author FOREIGN KEY (author_id) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_incident_chat_incident_created
    ON incident_chat_message(incident_id, created_at);

INSERT INTO incident_type(name, description, is_active)
SELECT 'Cocinado erróneo', 'Incidencia asociada a errores durante el proceso de cocinado', TRUE
WHERE NOT EXISTS (SELECT 1 FROM incident_type WHERE LOWER(name) = LOWER('Cocinado erróneo'));

INSERT INTO incident_type(name, description, is_active)
SELECT 'Producto en mal estado', 'Incidencia por producto en condiciones no aptas', TRUE
WHERE NOT EXISTS (SELECT 1 FROM incident_type WHERE LOWER(name) = LOWER('Producto en mal estado'));

INSERT INTO incident_type(name, description, is_active)
SELECT 'Otro', 'Incidencia genérica', TRUE
WHERE NOT EXISTS (SELECT 1 FROM incident_type WHERE LOWER(name) = LOWER('Otro'));
