-- Manual migration script for AI chat persistence
-- Note: project currently uses ddl-auto=validate, so schema is created manually.

CREATE TABLE IF NOT EXISTS ai_chat (
    ai_chat_id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    title VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    active_provider VARCHAR(20) NOT NULL,
    user_language VARCHAR(10) NOT NULL DEFAULT 'es',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP,
    message_count INTEGER NOT NULL DEFAULT 0,
    total_tokens_consumed BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_chat_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_chat_user_status
    ON ai_chat(user_id, status, last_message_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_chat_user_provider
    ON ai_chat(user_id, active_provider);

CREATE TABLE IF NOT EXISTS ai_chat_message (
    ai_chat_message_id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_name VARCHAR(100),
    tool_call_id VARCHAR(100),
    tool_result TEXT,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_msg_chat FOREIGN KEY (chat_id) REFERENCES ai_chat(ai_chat_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_msg_chat_created
    ON ai_chat_message(chat_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_msg_chat_role
    ON ai_chat_message(chat_id, role);

CREATE TABLE IF NOT EXISTS user_api_key (
    user_api_key_id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    provider VARCHAR(20) NOT NULL,
    encrypted_key VARCHAR(512) NOT NULL,
    key_hint VARCHAR(8) NOT NULL,
    encryption_key_version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_key_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_api_key_user_provider
    ON user_api_key(user_id, provider, active);

CREATE TABLE IF NOT EXISTS global_api_key (
    global_api_key_id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(20) NOT NULL UNIQUE,
    encrypted_key VARCHAR(512) NOT NULL,
    key_hint VARCHAR(8) NOT NULL,
    encryption_key_version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by INTEGER,
    CONSTRAINT fk_global_key_admin FOREIGN KEY (updated_by) REFERENCES users(user_id)
);

-- Migration: add thinking_content column to ai_chat_message
ALTER TABLE ai_chat_message ADD COLUMN IF NOT EXISTS thinking_content TEXT;
