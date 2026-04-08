import logging
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.db.outbox import init_db
from app.services.kafka_service import kafka_manager
from app.services.outbox_service import outbox_service

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
logging.getLogger("prophet").setLevel(logging.ERROR)
logging.getLogger("cmdstanpy").setLevel(logging.ERROR)


class HealthCheckAccessFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        message = record.getMessage()
        return "/health" not in message and "/ready" not in message


logging.getLogger("uvicorn.access").addFilter(HealthCheckAccessFilter())

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # ── Startup ───────────────────────────────────────────────────────
    logger.info(f"Starting {settings.APP_NAME}...")

    init_db()
    kafka_task = asyncio.create_task(kafka_manager.start(), name="kafka-consumer")
    relay_task = asyncio.create_task(outbox_service.relay_loop(), name="outbox-relay")
    logger.info("Predictor-service background tasks started")

    yield  # ← application runs here

    # ── Shutdown ──────────────────────────────────────────────────────
    logger.info(f"Shutting down {settings.APP_NAME}...")
    relay_task.cancel()
    try:
        await relay_task
    except asyncio.CancelledError:
        pass

    kafka_task.cancel()
    try:
        await kafka_task
    except asyncio.CancelledError:
        pass

    await kafka_manager.stop()
    logger.info("Shutdown complete")


app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    description="AI-powered demand forecasting microservice using Meta Prophet.",
    lifespan=lifespan,
)


@app.get("/health", tags=["observability"])
def health():
    """Liveness probe — used by Docker healthcheck and Eureka."""
    return JSONResponse({"status": "UP", "service": settings.APP_NAME})


@app.get("/ready", tags=["observability"])
def ready():
    """
    Readiness probe — checks that Kafka consumer is running.
    Returns 503 if the consumer task has crashed.
    """
    consumer_ok = kafka_manager._running and kafka_manager.consumer is not None
    status = "READY" if consumer_ok else "NOT_READY"
    code = 200 if consumer_ok else 503
    return JSONResponse({"status": status, "service": settings.APP_NAME}, status_code=code)


@app.get("/outbox/metrics", tags=["observability"])
def outbox_metrics():
    pending = outbox_service.pending_count()
    return JSONResponse(
        {"outbox_pending": pending, "service": settings.APP_NAME}
    )
