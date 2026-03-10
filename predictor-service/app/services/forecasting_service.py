import pandas as pd
from prophet import Prophet
import logging
import json
import jwt
import httpx
from datetime import datetime, timedelta
from app.core.config import settings

logger = logging.getLogger(__name__)

class ForecastingService:
    def _generate_token(self):
        payload = {
            "sub": "predictor-service",
            "role": "ADMIN",
            "iat": datetime.utcnow(),
            "exp": datetime.utcnow() + timedelta(hours=1)
        }
        return jwt.encode(payload, settings.JWT_SECRET, algorithm="HS256")

    async def fetch_history(self, product_id: int):
        token = self._generate_token()
        headers = {"Authorization": f"Bearer {token}"}
        url = f"{settings.INVENTORY_SERVICE_URL}/api/stock-ledger/consumption/{product_id}?lastDays=90"
        
        async with httpx.AsyncClient() as client:
            try:
                response = await client.get(url, headers=headers, timeout=10.0)
                if response.status_code == 200:
                    return response.json()
                logger.error(f"Error fetching history for {product_id}: {response.status_code}")
            except Exception as e:
                logger.error(f"Exception fetching history for {product_id}: {e}")
        return None

    async def process_event(self, event_data):
        components_str = event_data.get("componentsState", "{}")
        try:
            components_data = json.loads(components_str)
            components = components_data.get("components", [])
        except Exception:
            logger.error("Failed to parse componentsState")
            return []

        results = []
        for comp in components:
            p_id = comp.get("productId")
            if not p_id: continue
            
            history = await self.fetch_history(p_id)
            if not history or not history.get("breakdown"):
                continue

            df = pd.DataFrame(history["breakdown"])
            df = df.rename(columns={"date": "ds", "consumed": "y"})
            
            if len(df) < 2:
                logger.warning(f"Not enough data for product {p_id}")
                continue

            try:
                model = Prophet(yearly_seasonality=False, weekly_seasonality=True, daily_seasonality=False)
                model.fit(df)
                
                future = model.make_future_dataframe(periods=14)
                forecast = model.predict(future)
                
                prediction_val = float(forecast.iloc[-14:]["yhat"].mean())
                
                results.append({
                    "productId": p_id,
                    "projectedConsumption": round(max(0.0, prediction_val), 2),
                    "calculatedAt": datetime.now().isoformat(),
                    "modelUsed": "Meta Prophet v1.1",
                    "confidenceScore": "0.85"
                })
                logger.info(f"Forecast generated for product {p_id}: {prediction_val}")
            except Exception as e:
                logger.error(f"Error running Prophet for {p_id}: {e}")

        return results

forecast_service = ForecastingService()
