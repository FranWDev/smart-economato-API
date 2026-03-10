from fastapi import FastAPI
import py_eureka_client.eureka_client as eureka_client
import asyncio
import logging
from app.core.config import settings
from app.services.kafka_service import kafka_manager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title=settings.APP_NAME)

@app.on_event("startup")
async def startup_event():
    # Register with Eureka
    await eureka_client.init_async(
        eureka_server=settings.EUREKA_SERVER,
        app_name=settings.APP_NAME,
        instance_port=settings.INSTANCE_PORT,
        instance_host=settings.INSTANCE_HOST
    )
    logger.info("Registered with Eureka")
    
    # Start Kafka Consumer/Producer
    asyncio.create_task(kafka_manager.start())
    logger.info("Kafka Manager started")

@app.on_event("shutdown")
async def shutdown_event():
    await eureka_client.stop_async()
    await kafka_manager.stop()

@app.get("/health")
def health():
    return {"status": "UP"}
