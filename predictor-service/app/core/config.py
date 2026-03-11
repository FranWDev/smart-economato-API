import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "predictor-service"
    KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    
    # Internal Communication
    JWT_SECRET: str = os.getenv("JWT_SECRET", "changeit")
    INVENTORY_SERVICE_URL: str = os.getenv("INVENTORY_SERVICE_URL", "http://inventory-backend:8081")
    
    # Must match a real ADMIN user in the inventory DB so JwtFilter can load it
    PREDICTOR_USERNAME: str = os.getenv("PREDICTOR_USERNAME", "Admin")
    
    # Topics
    RECIPE_COOKING_TOPIC: str = "recipe-cooking-audit-events"
    FORECAST_UPDATES_TOPIC: str = "forecast-updates"
    
    # Force cmdstanpy backend for Prophet. This ensures the full Bayesian model
    # is trained instead of falling back to simple mean. cmdstanpy is pre-compiled
    # in the Dockerfile at /home/appuser/.cmdstan
    PROPHET_STAN_BACKEND: str = os.getenv("PROPHET_STAN_BACKEND", "CMDSTANPY")
    CMDSTAN_PATH: str = os.getenv("CMDSTAN_PATH", "/home/appuser/.cmdstan")

settings = Settings()
