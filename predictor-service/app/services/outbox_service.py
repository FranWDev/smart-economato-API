import asyncio
import json
import logging
import sqlite3
from datetime import datetime, timezone
from confluent_kafka import KafkaException, Producer
from app.core.config import settings
from app.db.outbox import get_connection

logger = logging.getLogger(__name__)

RELAY_INTERVAL_S: float = 5.0
RELAY_BATCH_SIZE: int = 50
KAFKA_FLUSH_TIMEOUT_S: int = 30
MAX_ATTEMPTS: int = 10


class OutboxService:

    def __init__(self) -> None:
        self._producer: Producer | None = None

    def save(self, topic: str, event_key: str, data: dict) -> None:
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

    async def relay_loop(self) -> None:
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

    def _process_batch(self) -> None:
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

            try:
                self._producer.produce(
                    row["topic"],
                    key=row["event_key"],
                    value=row["payload"].encode("utf-8"),
                    on_delivery=_delivery,
                )
                self._producer.flush(timeout=KAFKA_FLUSH_TIMEOUT_S)

                if ack_received:
                    sent_ids.append(row_id)
                elif broker_error:
                    failed_ids.append(row_id)
                    error_msgs[row_id] = broker_error[0]
                else:
                    # flush timed out — callback never fired
                    failed_ids.append(row_id)
                    error_msgs[row_id] = "delivery callback not invoked (flush timeout?)"

            except KafkaException as exc:
                logger.error("KafkaException for outbox row %s: %s", row_id, exc)
                failed_ids.append(row_id)
                error_msgs[row_id] = str(exc)
            except Exception as exc:
                logger.error("Unexpected error for outbox row %s: %s", row_id, exc)
                failed_ids.append(row_id)
                error_msgs[row_id] = str(exc)

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

    def pending_count(self) -> int:
        """Return the number of rows still waiting in the outbox."""
        with get_connection() as conn:
            row = conn.execute(
                "SELECT COUNT(*) AS cnt FROM forecast_outbox WHERE attempts < ?",
                (MAX_ATTEMPTS,),
            ).fetchone()
            return int(row["cnt"]) if row else 0


outbox_service = OutboxService()
