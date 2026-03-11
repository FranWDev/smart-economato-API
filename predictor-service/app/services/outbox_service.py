"""
Outbox Service — write side + relay side.

Write side
----------
``save()`` persists a forecast result dict to SQLite **inside the same
logical unit-of-work** as the Kafka consumer offset commit.  If the
process crashes after saving but before the relay runs, the row stays in
the table and will be delivered on the next relay cycle (at-least-once).

Relay side
----------
``relay_loop()`` is a long-running async coroutine that every
``RELAY_INTERVAL_S`` seconds:

1. Reads the next batch of pending rows (``attempts < MAX_ATTEMPTS``).
2. Produces each row to Kafka (``acks=all``).
3. Deletes acknowledged rows; increments ``attempts`` on failures.

This mirrors ``AuditOutboxProcessor.processOutbox()`` from the
inventory-service — same pattern, same guarantees.
"""

import asyncio
import json
import logging
import sqlite3
from datetime import datetime, timezone

from confluent_kafka import KafkaException, Producer

from app.core.config import settings
from app.db.outbox import get_connection

logger = logging.getLogger(__name__)

RELAY_INTERVAL_S: float = 5.0   # Poll interval — matches @Scheduled(fixedDelay=5000) in Java
RELAY_BATCH_SIZE: int = 50       # Same as findTop50ByOrderByCreatedAtAsc()
KAFKA_FLUSH_TIMEOUT_S: int = 30
MAX_ATTEMPTS: int = 10           # Dead-letter threshold before a row is abandoned


class OutboxService:
    """
    Encapsulates both the write path (``save``) and the relay path
    (``relay_loop``) of the Outbox Pattern.
    """

    def __init__(self) -> None:
        self._producer: Producer | None = None

    # ------------------------------------------------------------------
    # Write side (called by the Kafka consumer, synchronously)
    # ------------------------------------------------------------------

    def save(self, topic: str, event_key: str, data: dict) -> None:
        """
        Atomically persist a forecast result to the SQLite outbox.

        Mirrors ``AuditEventProducer.saveToOutbox()`` in the inventory-service.
        Must be called **before** committing the consumer offset so that
        no forecast is silently lost.
        """
        payload = json.dumps(data, ensure_ascii=False)
        created_at = datetime.now(tz=timezone.utc).isoformat()

        with get_connection() as conn:
            conn.execute(
                """
                INSERT INTO forecast_outbox (topic, event_key, payload, created_at)
                VALUES (?, ?, ?, ?)
                """,
                (topic, event_key, payload, created_at),
            )
            conn.commit()

        logger.debug("Outbox entry saved: topic=%s, key=%s", topic, event_key)

    # ------------------------------------------------------------------
    # Relay side (background coroutine, started from FastAPI lifespan)
    # ------------------------------------------------------------------

    async def relay_loop(self) -> None:
        """
        Long-running coroutine that relays pending outbox rows to Kafka.

        Mirrors ``AuditOutboxProcessor.processOutbox()`` scheduled at
        ``fixedDelay = 5 000 ms`` in the inventory-service.
        """
        self._producer = Producer(
            {
                "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
                "linger.ms": 5,
                "acks": "all",
            }
        )
        logger.info("Outbox relay started (interval=%ss, batch=%s)", RELAY_INTERVAL_S, RELAY_BATCH_SIZE)

        while True:
            try:
                await asyncio.sleep(RELAY_INTERVAL_S)
                # Blocking DB + Kafka I/O runs in a thread-executor to avoid
                # blocking the asyncio event loop — same approach as the
                # consumer's poll().
                loop = asyncio.get_running_loop()
                await loop.run_in_executor(None, self._process_batch)

            except asyncio.CancelledError:
                logger.info("Outbox relay cancelled — flushing producer")
                if self._producer:
                    self._producer.flush(timeout=10)
                raise

            except Exception as exc:
                logger.error("Outbox relay error: %s", exc, exc_info=True)
                # Do not crash — wait for next cycle

    # ------------------------------------------------------------------
    # Synchronous batch processor (runs inside a thread executor)
    # ------------------------------------------------------------------

    def _process_batch(self) -> None:
        """
        Read → produce → delete (or increment attempts).
        All SQLite access is synchronous; the executor prevents blocking the loop.
        """
        # --- 1. Read pending rows ---
        with get_connection() as conn:
            rows = conn.execute(
                """
                SELECT id, topic, event_key, payload, attempts
                FROM forecast_outbox
                WHERE attempts < ?
                ORDER BY created_at
                LIMIT ?
                """,
                (MAX_ATTEMPTS, RELAY_BATCH_SIZE),
            ).fetchall()

        if not rows:
            return

        sent_ids: list[int] = []
        failed_ids: list[int] = []
        error_msgs: dict[int, str] = {}

        # --- 2. Produce each row ---
        for row in rows:
            row_id: int = row["id"]
            ack_received: list[bool] = []
            broker_error: list[str] = []

            def _delivery(err, msg, _id=row_id):
                if err:
                    broker_error.append(str(err))
                    logger.warning(
                        "Kafka rejected outbox row %s: %s", _id, err
                    )
                else:
                    ack_received.append(True)
                    logger.debug(
                        "Outbox row %s ACK'd → %s[%s]@%s",
                        _id, msg.topic(), msg.partition(), msg.offset(),
                    )

            try:
                self._producer.produce(
                    row["topic"],
                    key=row["event_key"],
                    value=row["payload"].encode("utf-8"),
                    on_delivery=_delivery,
                )
                # Flush waits for broker ACK before moving to next row,
                # matching the Java future.get(5, TimeUnit.SECONDS) semantics.
                self._producer.flush(timeout=KAFKA_FLUSH_TIMEOUT_S)

                if broker_error:
                    failed_ids.append(row_id)
                    error_msgs[row_id] = broker_error[0]
                else:
                    sent_ids.append(row_id)

            except KafkaException as exc:
                logger.error("KafkaException for outbox row %s: %s", row_id, exc)
                failed_ids.append(row_id)
                error_msgs[row_id] = str(exc)
            except Exception as exc:
                logger.error("Unexpected error for outbox row %s: %s", row_id, exc)
                failed_ids.append(row_id)
                error_msgs[row_id] = str(exc)

        # --- 3. Update SQLite ---
        with get_connection() as conn:
            if sent_ids:
                placeholders = ",".join("?" * len(sent_ids))
                conn.execute(
                    f"DELETE FROM forecast_outbox WHERE id IN ({placeholders})",
                    sent_ids,
                )

            for failed_id in failed_ids:
                conn.execute(
                    """
                    UPDATE forecast_outbox
                    SET attempts   = attempts + 1,
                        last_error = ?
                    WHERE id = ?
                    """,
                    (error_msgs.get(failed_id, "unknown"), failed_id),
                )

            conn.commit()

        if sent_ids or failed_ids:
            logger.info(
                "Outbox relay batch done: sent=%d, failed=%d",
                len(sent_ids), len(failed_ids),
            )

    # ------------------------------------------------------------------
    # Observability helper (mirrors the Micrometer Gauge in Java)
    # ------------------------------------------------------------------

    def pending_count(self) -> int:
        """Return the number of rows still waiting in the outbox."""
        with get_connection() as conn:
            row = conn.execute(
                "SELECT COUNT(*) AS cnt FROM forecast_outbox WHERE attempts < ?",
                (MAX_ATTEMPTS,),
            ).fetchone()
            return int(row["cnt"]) if row else 0


outbox_service = OutboxService()
