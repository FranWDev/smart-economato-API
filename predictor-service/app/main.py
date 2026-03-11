import logging
import asyncio
from contextlib import asynccontextmanager

# ── LOGGING CONFIGURATION MUST BE FIRST ──────────────────────────────────
# Historically we ran into a bug during Prophet initialization where a
# missing backend would lead to an AttributeError being emitted at DEBUG
# log level.  We now handle that failure explicitly in the forecasting
# service, but we keep the logger seeding here so that the package doesn’t
# spam the console when a backend is present.
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
# Reduce noise from prophet/cmdstanpy during startup
logging.getLogger("prophet").setLevel(logging.ERROR)
logging.getLogger("cmdstanpy").setLevel(logging.ERROR)

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.db.outbox import init_db
from app.services.kafka_service import kafka_manager
from app.services.outbox_service import outbox_service

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    FastAPI modern lifespan handler.
    Replaces deprecated @app.on_event("startup"/"shutdown").
    """
    # ── Startup ───────────────────────────────────────────────────────
    logger.info(f"Starting {settings.APP_NAME}...")

    # Initialise the SQLite outbox schema (idempotent — safe to call every time)
    init_db()

    # Launch Kafka consumer in the background (non-blocking)
    kafka_task = asyncio.create_task(kafka_manager.start(), name="kafka-consumer")
    logger.info("Kafka consumer started")

    # Launch outbox relay in the background
    # Mirrors AuditOutboxProcessor @Scheduled(fixedDelay=5000) in inventory-service
    relay_task = asyncio.create_task(outbox_service.relay_loop(), name="outbox-relay")
    logger.info("Outbox relay started")

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
    """
    Returns the number of forecast rows still pending in the SQLite outbox.
    Mirrors the ``kafka.audit.outbox.pending`` Micrometer Gauge in the
    inventory-service.
    """
    pending = outbox_service.pending_count()
    return JSONResponse(
        {"outbox_pending": pending, "service": settings.APP_NAME}
    )
