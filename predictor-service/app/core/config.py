import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "predictor-service"
    EUREKA_SERVER: str = os.getenv("EUREKA_SERVER_URL", "http://localhost:8761/eureka")
    KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    
    # Internal Communication
    JWT_SECRET: str = os.getenv("JWT_SECRET", "changeit")
    INVENTORY_SERVICE_URL: str = os.getenv("INVENTORY_SERVICE_URL", "http://api-gateway:8080")
    
    # Must match a real ADMIN user in the inventory DB so JwtFilter can load it
    PREDICTOR_USERNAME: str = os.getenv("PREDICTOR_USERNAME", "Admin")
    
    # Topics
    RECIPE_COOKING_TOPIC: str = "recipe-cooking-audit-events"
    FORECAST_UPDATES_TOPIC: str = "forecast-updates"
    
    INSTANCE_HOST: str = os.getenv("INSTANCE_HOST", "predictor-service")
    INSTANCE_PORT: int = int(os.getenv("INSTANCE_PORT", "8000"))

settings = Settings()
