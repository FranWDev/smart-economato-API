-- Manual migration script for recipe draft persistence
-- Note: project currently uses ddl-auto=validate, so schema is created manually.

CREATE TABLE IF NOT EXISTS recipe_draft (
    recipe_draft_id SERIAL PRIMARY KEY,
    recipe_name VARCHAR(150) NOT NULL,
    elaboration TEXT,
    presentation TEXT,
    portions NUMERIC(10, 2) NOT NULL DEFAULT 1,
    components_json TEXT NOT NULL,
    allergen_ids_json TEXT,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_by INTEGER NOT NULL,
    reviewed_by INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    approved_recipe_id INTEGER,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_recipe_draft_created_by FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT fk_recipe_draft_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_recipe_draft_status ON recipe_draft(status);
CREATE INDEX IF NOT EXISTS idx_recipe_draft_created_by ON recipe_draft(created_by);