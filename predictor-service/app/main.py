import logging
import asyncio
from contextlib import asynccontextmanager

# ── LOGGING CONFIGURATION MUST BE FIRST ──────────────────────────────────
# This is critical to fix the Prophet 'stan_backend' AttributeError bug.
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
# Force silence for Prophet and its backend
logging.getLogger("prophet").setLevel(logging.ERROR)
logging.getLogger("cmdstanpy").setLevel(logging.ERROR)

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.services.kafka_service import kafka_manager

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    FastAPI modern lifespan handler.
    Replaces deprecated @app.on_event("startup"/"shutdown").
    """
    # ── Startup ───────────────────────────────────────────────────────
    logger.info(f"Starting {settings.APP_NAME}...")


    # Launch Kafka consumer in the background (non-blocking)
    kafka_task = asyncio.create_task(kafka_manager.start(), name="kafka-consumer")
    logger.info("Kafka consumer started")

    yield  # ← application runs here

    # ── Shutdown ──────────────────────────────────────────────────────
    logger.info(f"Shutting down {settings.APP_NAME}...")
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
