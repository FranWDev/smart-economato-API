-- Blockchain schema migration (manual execution, project uses ddl-auto=validate)

CREATE TABLE IF NOT EXISTS ledger_block (
    id BIGSERIAL PRIMARY KEY,
    block_number BIGINT NOT NULL UNIQUE,
    previous_block_hash VARCHAR(64) NOT NULL,
    merkle_root VARCHAR(64) NOT NULL,
    block_hash VARCHAR(64) NOT NULL UNIQUE,
    nonce BIGINT NOT NULL,
    difficulty INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    transaction_count INTEGER NOT NULL,
    hmac_key_version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_block_number ON ledger_block (block_number);
CREATE INDEX IF NOT EXISTS idx_block_hash ON ledger_block (block_hash);

ALTER TABLE stock_ledger
    ADD COLUMN IF NOT EXISTS block_id BIGINT;

ALTER TABLE stock_ledger
    ADD CONSTRAINT fk_ledger_block
    FOREIGN KEY (block_id) REFERENCES ledger_block(id);

CREATE INDEX IF NOT EXISTS idx_ledger_block_id ON stock_ledger (block_id);
CREATE INDEX IF NOT EXISTS idx_ledger_block_id_tx_id ON stock_ledger (block_id, transaction_id);

ALTER TABLE stock_snapshot
    ADD COLUMN IF NOT EXISTS last_block_number BIGINT;

ALTER TABLE stock_snapshot
    ADD COLUMN IF NOT EXISTS last_block_hash VARCHAR(64);

-- Blocks are immutable.
CREATE OR REPLACE FUNCTION prevent_ledger_block_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_block is immutable; % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ledger_block_no_update ON ledger_block;
CREATE TRIGGER trg_ledger_block_no_update
    BEFORE UPDATE ON ledger_block
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_block_mutation();

DROP TRIGGER IF EXISTS trg_ledger_block_no_delete ON ledger_block;
CREATE TRIGGER trg_ledger_block_no_delete
    BEFORE DELETE ON ledger_block
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_block_mutation();

-- Stock ledger is immutable except attaching a mined block_id.
CREATE OR REPLACE FUNCTION enforce_stock_ledger_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'stock_ledger is append-only; DELETE is not allowed';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.block_id IS NULL
           AND NEW.block_id IS NOT NULL
           AND NEW.transaction_id IS NOT DISTINCT FROM OLD.transaction_id
           AND NEW.current_hash IS NOT DISTINCT FROM OLD.current_hash
           AND NEW.previous_hash IS NOT DISTINCT FROM OLD.previous_hash
           AND NEW.quantity_delta IS NOT DISTINCT FROM OLD.quantity_delta
           AND NEW.resulting_stock IS NOT DISTINCT FROM OLD.resulting_stock
           AND NEW.movement_type IS NOT DISTINCT FROM OLD.movement_type
           AND NEW.description IS NOT DISTINCT FROM OLD.description
           AND NEW.transaction_timestamp IS NOT DISTINCT FROM OLD.transaction_timestamp
           AND NEW.sequence_number IS NOT DISTINCT FROM OLD.sequence_number
           AND NEW.verified IS NOT DISTINCT FROM OLD.verified
           AND NEW.product_id IS NOT DISTINCT FROM OLD.product_id
           AND NEW.user_id IS NOT DISTINCT FROM OLD.user_id
           AND NEW.order_id IS NOT DISTINCT FROM OLD.order_id
           AND NEW.expiration_date IS NOT DISTINCT FROM OLD.expiration_date
           AND NEW.correlation_id IS NOT DISTINCT FROM OLD.correlation_id THEN
            RETURN NEW;
        END IF;

        RAISE EXCEPTION 'stock_ledger is append-only; UPDATE is not allowed except assigning block_id';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_stock_ledger_no_update ON stock_ledger;
CREATE TRIGGER trg_stock_ledger_no_update
    BEFORE UPDATE ON stock_ledger
    FOR EACH ROW
    EXECUTE FUNCTION enforce_stock_ledger_immutability();

DROP TRIGGER IF EXISTS trg_stock_ledger_no_delete ON stock_ledger;
CREATE TRIGGER trg_stock_ledger_no_delete
    BEFORE DELETE ON stock_ledger
    FOR EACH ROW
    EXECUTE FUNCTION enforce_stock_ledger_immutability();
