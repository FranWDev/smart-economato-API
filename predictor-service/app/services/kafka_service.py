"""
Kafka Consumer for the predictor service.

Responsibility: consume ``recipe-cooking-audit-events``, run Prophet
forecasting, and **write results to the SQLite outbox**.

The outbox relay (``OutboxService.relay_loop``) is responsible for
forwarding those rows to the ``forecast-updates`` topic.  This consumer
no longer holds a Kafka producer — it only commits its consumer offset
**after** the outbox rows have been saved, guaranteeing at-least-once
delivery without the tight coupling of a synchronous produce-then-commit
dance.
"""

import json
import logging
import asyncio
from confluent_kafka import Consumer, KafkaError, KafkaException
from app.core.config import settings
from app.services.forecasting_service import forecast_service
from app.services.outbox_service import outbox_service

logger = logging.getLogger(__name__)

_RETRY_BACKOFF_S = 5.0
_POLL_TIMEOUT_S = 1.0


class KafkaManager:
    def __init__(self):
        self.consumer_conf = {
            "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
            "group.id": "predictor-consumer-group",
            "auto.offset.reset": "earliest",
            # Manual commit — we commit ONLY after outbox rows are persisted
            "enable.auto.commit": False,
        }
        self.consumer: Consumer | None = None
        self._running = False
        self._loop: asyncio.AbstractEventLoop | None = None

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------
    async def start(self):
        """Entry point called from FastAPI lifespan. Runs the consumer loop."""
        self._loop = asyncio.get_running_loop()
        self._running = True

        # Reintentar conexión si Kafka aún no está listo
        while self._running:
            try:
                await self._connect()
                logger.info(f"Subscribed to topic: {settings.RECIPE_COOKING_TOPIC}")
                await self._consume_loop()
            except KafkaException as exc:
                logger.error(f"KafkaException in consumer — restarting in {_RETRY_BACKOFF_S}s: {exc}")
                await asyncio.sleep(_RETRY_BACKOFF_S)
            except Exception as exc:
                logger.error(f"Unexpected error in consumer — restarting in {_RETRY_BACKOFF_S}s: {exc}")
                await asyncio.sleep(_RETRY_BACKOFF_S)
            finally:
                self._disconnect()

    async def stop(self):
        """Graceful shutdown."""
        self._running = False
        self._disconnect()

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------
    async def _connect(self):
        self.consumer = Consumer(self.consumer_conf)
        self.consumer.subscribe([settings.RECIPE_COOKING_TOPIC])

    def _disconnect(self):
        if self.consumer:
            try:
                self.consumer.close()
            except Exception:
                pass
            self.consumer = None

    async def _consume_loop(self):
        """
        Poll is blocking (librdkafka C extension).
        We offload it to a thread-pool executor so as NOT to block the
        asyncio event loop — this is the critical fix for uvicorn compatibility.
        """
        loop = asyncio.get_running_loop()

        while self._running:
            # Run the blocking poll in a thread executor
            msg = await loop.run_in_executor(
                None, lambda: self.consumer.poll(_POLL_TIMEOUT_S)
            )

            if msg is None:
                continue

            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                logger.error(f"Kafka consumer error: {msg.error()}")
                raise KafkaException(msg.error())

            try:
                payload = json.loads(msg.value().decode("utf-8"))
                logger.info(f"Received cooking event for recipe {payload.get('recipeId')}")

                results = await forecast_service.process_event(payload)

                # ── Outbox pattern ────────────────────────────────────────────
                # Write ALL forecast results to SQLite BEFORE committing the
                # consumer offset.  If the process dies after this point the
                # outbox relay will publish the rows on restart (at-least-once).
                # The relay (OutboxService.relay_loop) handles actual Kafka
                # production — this consumer no longer talks to Kafka as a
                # producer.
                if results:
                    loop = asyncio.get_running_loop()
                    for result in results:
                        await loop.run_in_executor(
                            None,
                            lambda r=result: outbox_service.save(
                                topic=settings.FORECAST_UPDATES_TOPIC,
                                event_key=str(r.get("productId", "")),
                                data=r,
                            ),
                        )
                    logger.info(
                        f"Saved {len(results)} forecast result(s) to outbox "
                        f"for recipe {payload.get('recipeId')}"
                    )

                # Commit offset only after outbox rows are durably persisted
                await loop.run_in_executor(None, self.consumer.commit)

            except json.JSONDecodeError as exc:
                logger.error(f"Malformed JSON in Kafka message — skipping: {exc}")
                # Poison pill — commit to avoid infinite redelivery
                loop = asyncio.get_running_loop()
                await loop.run_in_executor(None, self.consumer.commit)
            except Exception as exc:
                logger.error(f"Error processing message: {exc}", exc_info=True)
                # Do NOT commit — cooking event will be redelivered on restart

    def _enqueue_forecast_update(self, data: dict):
        """Removed — production is now handled by OutboxService.relay_loop."""
        raise NotImplementedError(
            "_enqueue_forecast_update was removed. Use outbox_service.save() instead."
        )


kafka_manager = KafkaManager()
