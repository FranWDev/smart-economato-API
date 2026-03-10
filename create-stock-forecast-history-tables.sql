BEGIN;

-- =====================================================
-- 1) Tabla principal: stock_daily_forecast
-- =====================================================
CREATE TABLE IF NOT EXISTS stock_daily_forecast (
    product_id     INTEGER PRIMARY KEY,
    horizon_days   INTEGER NOT NULL,
    calculated_at  TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    CONSTRAINT fk_stock_daily_forecast_product
        FOREIGN KEY (product_id)
        REFERENCES product(product_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_stock_daily_forecast_horizon_days
        CHECK (horizon_days > 0)
);

-- Valores diarios (ElementCollection)
CREATE TABLE IF NOT EXISTS stock_daily_forecast_value (
    product_id      INTEGER NOT NULL,
    day_index       INTEGER NOT NULL,
    forecast_value  NUMERIC(19,4) NOT NULL,
    CONSTRAINT pk_stock_daily_forecast_value
        PRIMARY KEY (product_id, day_index),
    CONSTRAINT fk_stock_daily_forecast_value_parent
        FOREIGN KEY (product_id)
        REFERENCES stock_daily_forecast(product_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_stock_daily_forecast_value_day_index
        CHECK (day_index >= 0),
    CONSTRAINT chk_stock_daily_forecast_value_non_negative
        CHECK (forecast_value >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_daily_forecast_value_product
    ON stock_daily_forecast_value(product_id);

-- =====================================================
-- 2) Tabla principal: stock_weekly_consumption_history
-- =====================================================
CREATE TABLE IF NOT EXISTS stock_weekly_consumption_history (
    product_id        INTEGER PRIMARY KEY,
    weeks_of_history  INTEGER NOT NULL,
    calculated_at     TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    CONSTRAINT fk_stock_weekly_history_product
        FOREIGN KEY (product_id)
        REFERENCES product(product_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_stock_weekly_history_weeks
        CHECK (weeks_of_history > 0)
);

-- Valores semanales (ElementCollection)
CREATE TABLE IF NOT EXISTS stock_weekly_consumption_value (
    product_id         INTEGER NOT NULL,
    week_index         INTEGER NOT NULL,
    consumption_value  NUMERIC(19,3) NOT NULL,
    CONSTRAINT pk_stock_weekly_consumption_value
        PRIMARY KEY (product_id, week_index),
    CONSTRAINT fk_stock_weekly_consumption_value_parent
        FOREIGN KEY (product_id)
        REFERENCES stock_weekly_consumption_history(product_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_stock_weekly_consumption_value_week_index
        CHECK (week_index >= 0),
    CONSTRAINT chk_stock_weekly_consumption_value_non_negative
        CHECK (consumption_value >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_weekly_consumption_value_product
    ON stock_weekly_consumption_value(product_id);

COMMIT;
