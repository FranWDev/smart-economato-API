import json
import logging
import asyncio
from confluent_kafka import Consumer, Producer, KafkaError, KafkaException
from app.core.config import settings
from app.services.forecasting_service import forecast_service

logger = logging.getLogger(__name__)

_RETRY_BACKOFF_S = 5.0
_POLL_TIMEOUT_S = 1.0


class KafkaManager:
    def __init__(self):
        self.consumer_conf = {
            "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
            "group.id": "predictor-consumer-group",
            "auto.offset.reset": "earliest",
            # Commit manual para garantizar at-least-once processing
            "enable.auto.commit": False,
        }
        self.producer_conf = {
            "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
            # Linger permite micro-batching sin bloquear
            "linger.ms": 50,
            "acks": "all",
        }
        self.consumer: Consumer | None = None
        self.producer: Producer | None = None
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
        self.producer = Producer(self.producer_conf)
        self.consumer.subscribe([settings.RECIPE_COOKING_TOPIC])

    def _disconnect(self):
        if self.consumer:
            try:
                self.consumer.close()
            except Exception:
                pass
            self.consumer = None
        if self.producer:
            try:
                self.producer.flush(timeout=5)
            except Exception:
                pass
            self.producer = None

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

                for result in results:
                    self._send_forecast_update(result)

                # Commit AFTER successful processing (at-least-once semantics)
                await loop.run_in_executor(None, self.consumer.commit)

            except json.JSONDecodeError as exc:
                logger.error(f"Malformed JSON in Kafka message — skipping: {exc}")
                await loop.run_in_executor(None, self.consumer.commit)
            except Exception as exc:
                logger.error(f"Error processing message: {exc}", exc_info=True)
                # Do NOT commit — message will be redelivered on restart

    def _send_forecast_update(self, data: dict):
        """Non-blocking produce. Delivery report errors are logged via callback."""
        if not self.producer:
            logger.error("Producer not initialised — cannot send forecast update")
            return

        def _delivery_report(err, msg):
            if err:
                logger.error(
                    f"Failed delivering forecast update for product "
                    f"{data.get('productId')}: {err}"
                )
            else:
                logger.debug(
                    f"Delivered forecast update for product {data.get('productId')} "
                    f"to {msg.topic()} [{msg.partition()}]"
                )

        try:
            self.producer.produce(
                settings.FORECAST_UPDATES_TOPIC,
                key=str(data.get("productId", "")),
                value=json.dumps(data, ensure_ascii=False).encode("utf-8"),
                on_delivery=_delivery_report,
            )
            # poll() drains delivery callbacks without blocking
            self.producer.poll(0)
        except BufferError:
            logger.warning("Producer queue full — flushing and retrying")
            self.producer.flush(timeout=10)
            self._send_forecast_update(data)
        except Exception as exc:
            logger.error(f"Error producing forecast update: {exc}", exc_info=True)


kafka_manager = KafkaManager()
