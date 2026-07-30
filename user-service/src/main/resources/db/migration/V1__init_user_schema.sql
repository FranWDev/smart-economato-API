-- Flyway Migration V1: Initial User Schema for user-service

CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    "user" VARCHAR(100) NOT NULL UNIQUE,
    is_first_login BOOLEAN NOT NULL DEFAULT TRUE,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    teacher_id INT REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_user ON users("\"user\"");
CREATE INDEX IF NOT EXISTS idx_user_role ON users(role);

CREATE TABLE IF NOT EXISTS revoked_tokens (
    token_id BIGSERIAL PRIMARY KEY,
    token VARCHAR(1024) NOT NULL UNIQUE,
    revocation_date TIMESTAMP NOT NULL,
    expiration_date TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_revoked_token_token ON revoked_tokens(token);
CREATE INDEX IF NOT EXISTS idx_revoked_token_expiration ON revoked_tokens(expiration_date);

CREATE TABLE IF NOT EXISTS global_api_key (
    global_api_key_id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(20) NOT NULL UNIQUE,
    encrypted_key VARCHAR(512) NOT NULL,
    key_hint VARCHAR(8) NOT NULL,
    encryption_key_version INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by INT REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS user_api_key (
    user_api_key_id BIGSERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    encrypted_key VARCHAR(512) NOT NULL,
    key_hint VARCHAR(8) NOT NULL,
    encryption_key_version INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_api_key_user_provider ON user_api_key(user_id, provider, active);

CREATE TABLE IF NOT EXISTS temporary_role_escalation (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    expiration_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_escalation_user_id ON temporary_role_escalation(user_id);
CREATE INDEX IF NOT EXISTS idx_escalation_expiration ON temporary_role_escalation(expiration_time);

CREATE TABLE IF NOT EXISTS user_activity_log (
    id BIGSERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    screen VARCHAR(100),
    screen_context VARCHAR(255),
    session_id VARCHAR(100),
    timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_activity_user_id ON user_activity_log(user_id);
CREATE INDEX IF NOT EXISTS idx_activity_timestamp ON user_activity_log(timestamp);
