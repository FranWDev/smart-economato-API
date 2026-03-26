"""
Unit tests for the Outbox pattern implementation.

Mirrors the philosophy of ForecastResultConsumerTest.java in the
inventory-service: isolated, no real Kafka broker needed.
"""

import asyncio
import json
import sqlite3
import tempfile
import os
from pathlib import Path
from unittest.mock import MagicMock, patch, call

import pytest

# ---------------------------------------------------------------------------
# Helpers — create an isolated in-memory DB for every test
# ---------------------------------------------------------------------------

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS forecast_outbox (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    topic       TEXT    NOT NULL,
    event_key   TEXT    NOT NULL,
    payload     TEXT    NOT NULL,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    attempts    INTEGER NOT NULL DEFAULT 0,
    last_error  TEXT
);
CREATE INDEX IF NOT EXISTS idx_forecast_outbox_created
    ON forecast_outbox (created_at);
"""


def _make_conn() -> sqlite3.Connection:
    """Return a new in-memory SQLite connection with the outbox schema."""
    conn = sqlite3.connect(":memory:", check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA_SQL)
    return conn


def _insert_row(conn, topic="forecast-updates", key="42", payload=None, attempts=0):
    if payload is None:
        payload = json.dumps({"productId": 42, "projectedConsumption": 3.5})
    conn.execute(
        "INSERT INTO forecast_outbox (topic, event_key, payload, attempts) VALUES (?,?,?,?)",
        (topic, key, payload, attempts),
    )
    conn.commit()


# ---------------------------------------------------------------------------
# Tests — OutboxService.save()
# ---------------------------------------------------------------------------

class TestOutboxSave:
    """save() must atomically insert a row and nothing more."""

    def test_save_inserts_row(self, tmp_path):
        db_path = str(tmp_path / "outbox.db")

        with patch("app.db.outbox.settings") as mock_settings:
            mock_settings.SQLITE_DB_PATH = db_path

            from app.db.outbox import init_db, get_connection
            # re-patch at module level
            import app.db.outbox as outbox_mod
            outbox_mod.settings.SQLITE_DB_PATH = db_path
            init_db()

            from app.services.outbox_service import OutboxService
            svc = OutboxService()

            with patch("app.services.outbox_service.get_connection", side_effect=lambda: get_connection()):
                svc.save("forecast-updates", "7", {"productId": 7, "projectedConsumption": 5.0})

            conn = get_connection()
            rows = conn.execute("SELECT * FROM forecast_outbox").fetchall()
            assert len(rows) == 1
            assert rows[0]["topic"] == "forecast-updates"
            assert rows[0]["event_key"] == "7"
            data = json.loads(rows[0]["payload"])
            assert data["productId"] == 7
            assert rows[0]["attempts"] == 0

    def test_save_persists_unicode(self, tmp_path):
        """Payload with non-ASCII characters must survive round-trip."""
        db_path = str(tmp_path / "outbox.db")

        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = db_path
        from app.db.outbox import init_db, get_connection
        init_db()

        from app.services.outbox_service import OutboxService
        svc = OutboxService()

        data = {"modelUsed": "Profeta Básico ñ"}
        svc.save("forecast-updates", "1", data)

        conn = get_connection()
        row = conn.execute("SELECT payload FROM forecast_outbox").fetchone()
        assert "Profeta Básico ñ" in row["payload"]


# ---------------------------------------------------------------------------
# Tests — OutboxService.pending_count()
# ---------------------------------------------------------------------------

class TestPendingCount:
    def test_zero_when_empty(self, tmp_path):
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db
        init_db()

        from app.services.outbox_service import OutboxService
        svc = OutboxService()
        assert svc.pending_count() == 0

    def test_counts_pending_rows(self, tmp_path):
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db, get_connection
        init_db()

        conn = get_connection()
        for i in range(3):
            _insert_row(conn, key=str(i))

        from app.services.outbox_service import OutboxService
        svc = OutboxService()
        assert svc.pending_count() == 3

    def test_does_not_count_dead_letter_rows(self, tmp_path):
        """Rows that have reached MAX_ATTEMPTS must not show as pending."""
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db, get_connection
        init_db()

        conn = get_connection()
        _insert_row(conn, key="ok", attempts=0)      # pending
        _insert_row(conn, key="dead", attempts=10)   # dead-letter (== MAX_ATTEMPTS)

        from app.services.outbox_service import OutboxService, MAX_ATTEMPTS
        assert MAX_ATTEMPTS == 10
        svc = OutboxService()
        assert svc.pending_count() == 1


# ---------------------------------------------------------------------------
# Tests — OutboxService._process_batch()
# ---------------------------------------------------------------------------

class TestProcessBatch:
    """
    _process_batch() runs synchronously in an executor, so we can test it
    directly without asyncio here.
    """

    def _make_service_with_db(self, tmp_path):
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db
        init_db()
        from app.services.outbox_service import OutboxService
        return OutboxService()

    def test_sends_pending_row_and_deletes_it(self, tmp_path):
        """Happy path: one row → produced → deleted from outbox."""
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db, get_connection
        init_db()

        conn = get_connection()
        _insert_row(conn, key="99")

        from app.services.outbox_service import OutboxService

        mock_producer = MagicMock()
        ack_calls = []

        def fake_produce(topic, key, value, on_delivery):
            # simulate immediate ACK
            on_delivery(None, MagicMock())

        def fake_flush(timeout):
            pass

        mock_producer.produce = fake_produce
        mock_producer.flush = fake_flush

        svc = OutboxService()
        svc._producer = mock_producer
        svc._process_batch()

        remaining = conn.execute("SELECT COUNT(*) FROM forecast_outbox").fetchone()[0]
        assert remaining == 0

    def test_increments_attempts_on_broker_error(self, tmp_path):
        """When the broker rejects a message, attempts must increase."""
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db, get_connection
        init_db()

        conn = get_connection()
        _insert_row(conn, key="11", attempts=0)

        from app.services.outbox_service import OutboxService
        from confluent_kafka import KafkaError

        mock_producer = MagicMock()

        def fake_produce(topic, key, value, on_delivery):
            # simulate broker error
            mock_err = MagicMock()
            mock_err.__str__ = lambda self: "broker error"
            on_delivery(mock_err, None)

        mock_producer.produce = fake_produce
        mock_producer.flush = MagicMock()

        svc = OutboxService()
        svc._producer = mock_producer
        svc._process_batch()

        row = conn.execute("SELECT attempts, last_error FROM forecast_outbox").fetchone()
        assert row["attempts"] == 1
        assert row["last_error"] is not None

    def test_no_op_when_outbox_empty(self, tmp_path):
        """Empty outbox → producer is never called."""
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db
        init_db()

        from app.services.outbox_service import OutboxService

        mock_producer = MagicMock()
        svc = OutboxService()
        svc._producer = mock_producer
        svc._process_batch()

        mock_producer.produce.assert_not_called()

    def test_dead_letter_rows_are_skipped(self, tmp_path):
        """Rows at MAX_ATTEMPTS threshold must never be produced."""
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db, get_connection
        init_db()

        conn = get_connection()
        _insert_row(conn, key="dead", attempts=10)  # == MAX_ATTEMPTS

        from app.services.outbox_service import OutboxService
        mock_producer = MagicMock()
        svc = OutboxService()
        svc._producer = mock_producer
        svc._process_batch()

        mock_producer.produce.assert_not_called()

    def test_increments_attempts_on_flush_timeout(self, tmp_path):
        """When flush() times out (callback never fired), attempts must increase."""
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db, get_connection
        init_db()

        conn = get_connection()
        _insert_row(conn, key="timeout-row", attempts=0)

        from app.services.outbox_service import OutboxService

        mock_producer = MagicMock()

        def fake_produce(topic, key, value, on_delivery):
            # simulate no callback being fired (e.g. timeout)
            pass

        mock_producer.produce = fake_produce
        mock_producer.flush = MagicMock() # flush returns but callback wasn't called

        svc = OutboxService()
        svc._producer = mock_producer
        svc._process_batch()

        row = conn.execute("SELECT attempts, last_error FROM forecast_outbox").fetchone()
        assert row["attempts"] == 1
        assert "flush timeout" in row["last_error"]
        
        # Verify it wasn't deleted
        remaining = conn.execute("SELECT COUNT(*) FROM forecast_outbox").fetchone()[0]
        assert remaining == 1



# ---------------------------------------------------------------------------
# Tests — relay_loop() (async)
# ---------------------------------------------------------------------------

class TestRelayLoop:
    @pytest.mark.asyncio
    async def test_relay_loop_cancels_cleanly(self, tmp_path):
        """relay_loop must flush the producer and raise CancelledError on cancel."""
        import app.db.outbox as outbox_mod
        outbox_mod.settings.SQLITE_DB_PATH = str(tmp_path / "outbox.db")
        from app.db.outbox import init_db
        init_db()

        from app.services.outbox_service import OutboxService

        mock_producer = MagicMock()
        mock_producer.flush = MagicMock()

        with patch("app.services.outbox_service.Producer", return_value=mock_producer):
            svc = OutboxService()
            task = asyncio.create_task(svc.relay_loop())
            await asyncio.sleep(0.1)  # let the loop start
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task

        mock_producer.flush.assert_called()
