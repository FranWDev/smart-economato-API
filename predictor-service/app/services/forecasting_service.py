import asyncio
import logging
import json
from datetime import datetime, timezone, timedelta

import httpx
import jwt
import pandas as pd
from prophet import Prophet

from app.core.config import settings

logger = logging.getLogger(__name__)

# Prophet is CPU-heavy — run in a thread pool to avoid blocking uvicorn
_EXECUTOR = None  # uses default ThreadPoolExecutor


def _run_prophet(df: pd.DataFrame) -> tuple[float, float]:
    """
    Trains Prophet model and returns (mean_predicted_14d, confidence_score).
    Executed in a thread executor — NEVER call from the event loop directly.
    """
    model = Prophet(
        yearly_seasonality=False,
        weekly_seasonality=True,
        daily_seasonality=False,
        interval_width=0.80,
    )
    model.fit(df)

    future = model.make_future_dataframe(periods=14)
    forecast = model.predict(future)

    last_14 = forecast.iloc[-14:]

    mean_yhat = float(last_14["yhat"].mean())
    # Derive a confidence score from the mean width of the 80% interval
    interval_width = float((last_14["yhat_upper"] - last_14["yhat_lower"]).mean())
    # Narrower interval relative to prediction → higher confidence (clamp 0–1)
    if mean_yhat > 0:
        relative_uncertainty = min(interval_width / (abs(mean_yhat) + 1e-9), 1.0)
        confidence = round(1.0 - relative_uncertainty * 0.5, 4)
    else:
        confidence = 0.5

    return max(0.0, mean_yhat), confidence


class ForecastingService:
    # ------------------------------------------------------------------
    # Internal auth helpers
    # ------------------------------------------------------------------
    def _signing_key(self) -> bytes:
        """
        Java's JwtUtils uses Apache Commons Codec Base64 which is LENIENT about
        padding — it decodes strings whose length is not a multiple of 4 without
        throwing an error.  Python's base64.b64decode() is STRICT by default.

        We replicate Java's lenient behaviour by adding the exact padding chars
        needed before decoding:  padding = (4 - len(s) % 4) % 4

        Example: 43-char secret → 43%4=3 → add 1 '=' → 44 chars → decodes OK.
        """
        import base64 as _b64
        raw = settings.JWT_SECRET
        # Add just enough '=' padding to make the length a multiple of 4
        padding = (4 - len(raw) % 4) % 4
        padded = raw + "=" * padding
        try:
            key = _b64.b64decode(padded)
            logger.debug(f"JWT key: decoded {len(raw)}-char Base64 → {len(key)} bytes")
            return key
        except Exception as exc:
            logger.warning(f"Base64 decode failed ({exc}), using raw UTF-8 bytes")
            return raw.encode("utf-8")

    def _generate_token(self) -> str:
        """
        Generates a JWT compatible with Java's JwtFilter.
        sub must be a real DB username so loadUserByUsername() succeeds.
        """
        now = datetime.now(tz=timezone.utc)
        payload = {
            "sub": settings.PREDICTOR_USERNAME,
            "role": "ADMIN",
            "iat": now,
            "exp": now + timedelta(hours=1),
        }
        return jwt.encode(payload, self._signing_key(), algorithm="HS256")



    # ------------------------------------------------------------------
    # Data fetching
    # ------------------------------------------------------------------
    async def fetch_history(self, product_id: int) -> dict | None:
        """
        Fetches 90-day consumption history from the inventory-service via gateway.

        Two distinct 404 cases:
        - Gateway 404 (Eureka not synced yet): body contains 'requestId' field.
          → Retry with exponential backoff (gateway will sync within ~30s).
        - Backend 404 (product not found): body contains 'message' or no 'requestId'.
          → Skip immediately, no point retrying.
        """
        token = self._generate_token()
        url = (
            f"{settings.INVENTORY_SERVICE_URL}"
            f"/api/stock-ledger/consumption/{product_id}?lastDays=90"
        )
        headers = {"Authorization": f"Bearer {token}"}

        max_retries   = 4
        backoff_secs  = [5, 10, 20, 40]   # cumulative wait: up to ~75s

        for attempt in range(max_retries + 1):
            try:
                async with httpx.AsyncClient(timeout=httpx.Timeout(15.0)) as client:
                    response = await client.get(url, headers=headers)

                    if response.status_code == 200:
                        return response.json()

                    if response.status_code == 404:
                        try:
                            body = response.json()
                        except Exception:
                            body = {}

                        # Gateway 404: has 'requestId' → Eureka hasn't synced yet
                        if "requestId" in body and attempt < max_retries:
                            wait = backoff_secs[attempt]
                            logger.warning(
                                f"Gateway Eureka not ready for product {product_id} "
                                f"(attempt {attempt+1}/{max_retries}) — retrying in {wait}s"
                            )
                            await asyncio.sleep(wait)
                            continue

                        # Backend 404 or exhausted retries → skip
                        logger.warning(f"No history for product {product_id} — skipping")
                        return None

                    # Any other non-2xx
                    response.raise_for_status()

            except httpx.HTTPStatusError as exc:
                logger.error(
                    f"HTTP {exc.response.status_code} fetching history for product {product_id}"
                )
            except httpx.RequestError as exc:
                if attempt < max_retries:
                    wait = backoff_secs[attempt]
                    logger.warning(f"Network error for product {product_id}, retry in {wait}s: {exc}")
                    await asyncio.sleep(wait)
                    continue
                logger.error(f"Network error fetching history for product {product_id}: {exc}")
            except Exception as exc:
                logger.error(f"Unexpected error fetching history for product {product_id}: {exc}")
            return None

        return None


    # ------------------------------------------------------------------
    # Core forecast logic
    # ------------------------------------------------------------------
    async def process_event(self, event_data: dict) -> list[dict]:
        """
        Processes a recipe-cooking event and returns a list of forecast result dicts,
        one per ingredient that had enough historical data.
        """
        components_raw = event_data.get("componentsState", "{}")
        try:
            components_data = json.loads(components_raw) if isinstance(components_raw, str) else components_raw
            components = components_data.get("components", [])
        except (json.JSONDecodeError, AttributeError):
            logger.error("Failed to parse componentsState — skipping event")
            return []

        if not components:
            logger.warning(f"No components found in event for recipe {event_data.get('recipeId')}")
            return []

        results = []
        loop = asyncio.get_running_loop()

        for comp in components:
            p_id = comp.get("productId")
            if not p_id:
                continue

            history = await self.fetch_history(p_id)
            if not history or not history.get("breakdown"):
                logger.warning(f"No history for product {p_id} — skipping")
                continue

            breakdown = history["breakdown"]
            df = pd.DataFrame(breakdown)

            # Validate required columns
            if "date" not in df.columns or "consumed" not in df.columns:
                logger.error(f"Unexpected history schema for product {p_id}: {df.columns.tolist()}")
                continue

            df = df.rename(columns={"date": "ds", "consumed": "y"})
            df["ds"] = pd.to_datetime(df["ds"], errors="coerce")
            df = df.dropna(subset=["ds", "y"])

            if len(df) < 2:
                logger.warning(f"Not enough data points for product {p_id} ({len(df)} rows) — skipping")
                continue

            try:
                # Run blocking Prophet in thread pool so uvicorn stays responsive
                prediction, confidence = await loop.run_in_executor(
                    _EXECUTOR, _run_prophet, df
                )

                results.append({
                    "productId": p_id,
                    "projectedConsumption": round(prediction, 2),
                    "calculatedAt": datetime.now(tz=timezone.utc).isoformat(),
                    "modelUsed": "Meta Prophet v1.1",
                    "confidenceScore": confidence,
                    "forecastHorizonDays": 14,
                })
                logger.info(
                    f"Forecast ready for product {p_id}: "
                    f"consumption={round(prediction, 2)}, confidence={confidence}"
                )
            except Exception as exc:
                logger.error(f"Prophet failed for product {p_id}: {exc}", exc_info=True)

        return results


forecast_service = ForecastingService()
