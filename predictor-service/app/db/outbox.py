"""
SQLite-backed Outbox for forecast results.

Mirrors the ``AuditOutbox`` entity / ``audit_outbox`` table from the
inventory-service, using the stdlib ``sqlite3`` module so no extra
dependencies are required.

Schema
------
forecast_outbox
  id          INTEGER   PK AUTOINCREMENT
  topic       TEXT      Kafka topic name  (forecast-updates)
  event_key   TEXT      Kafka message key (product_id as string)
  payload     TEXT      JSON-serialised forecast result
  created_at  TEXT      ISO-8601 UTC timestamp
  attempts    INTEGER   Number of failed relay attempts (default 0)
  last_error  TEXT      Description of last relay error (nullable)
"""

import logging
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from app.core.config import settings

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Connection helper
# ---------------------------------------------------------------------------

def get_connection() -> sqlite3.Connection:
    """Return a new sqlite3 connection with row_factory set."""
    conn = sqlite3.connect(settings.SQLITE_DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    # WAL: readers never block writers and writers never block readers.
    conn.execute("PRAGMA journal_mode=WAL")
    # busy_timeout: if two writers collide (consumer INSERT vs relay DELETE),
    # SQLite retries for up to 3 000 ms before raising OperationalError.
    # Without this, writer-writer contention raises SQLITE_BUSY immediately.
    conn.execute("PRAGMA busy_timeout=3000")
    return conn


# ---------------------------------------------------------------------------
# Schema initialisation (called once at startup)
# ---------------------------------------------------------------------------

def init_db() -> None:
    """Create the outbox table and index if they do not already exist."""
    Path(settings.SQLITE_DB_PATH).parent.mkdir(parents=True, exist_ok=True)

    with get_connection() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS forecast_outbox (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                topic       TEXT    NOT NULL,
                event_key   TEXT    NOT NULL,
                payload     TEXT    NOT NULL,
                created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
                attempts    INTEGER NOT NULL DEFAULT 0,
                last_error  TEXT
            )
            """
        )
        conn.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_forecast_outbox_created
            ON forecast_outbox (created_at)
            """
        )
        conn.commit()

    logger.info("Outbox DB initialised at %s", settings.SQLITE_DB_PATH)
