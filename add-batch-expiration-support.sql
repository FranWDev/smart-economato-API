BEGIN;

ALTER TABLE IF EXISTS stock_ledger
    ADD COLUMN IF NOT EXISTS expiration_date DATE;

CREATE INDEX IF NOT EXISTS idx_ledger_expiration
    ON stock_ledger(expiration_date);

CREATE TABLE IF NOT EXISTS product_batch (
    batch_id BIGSERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL,
    expiration_date DATE,
    initial_quantity NUMERIC(10,3) NOT NULL,
    remaining_quantity NUMERIC(10,3) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    ledger_transaction_id BIGINT,
    is_depleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT,
    CONSTRAINT fk_batch_product
        FOREIGN KEY (product_id)
        REFERENCES product(product_id),
    CONSTRAINT fk_batch_ledger
        FOREIGN KEY (ledger_transaction_id)
        REFERENCES stock_ledger(transaction_id),
    CONSTRAINT chk_batch_initial_quantity_non_negative
        CHECK (initial_quantity >= 0),
    CONSTRAINT chk_batch_remaining_quantity_non_negative
        CHECK (remaining_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_batch_product
    ON product_batch(product_id);

CREATE INDEX IF NOT EXISTS idx_batch_expiration
    ON product_batch(expiration_date);

CREATE INDEX IF NOT EXISTS idx_batch_remaining
    ON product_batch(remaining_quantity);

COMMIT;
