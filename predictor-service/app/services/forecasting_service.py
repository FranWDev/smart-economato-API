import asyncio
import logging
import json
import os
from datetime import datetime, timezone, timedelta

import pandas as pd
from prophet import Prophet
import cmdstanpy

from app.core.config import settings

logger = logging.getLogger(__name__)

# Prophet is CPU-heavy — run in a thread pool to avoid blocking uvicorn
_EXECUTOR = None  # uses default ThreadPoolExecutor

# Silence prophet logs that trigger a known bug during __init__ when logging level is low
logging.getLogger("prophet").setLevel(logging.ERROR)
logging.getLogger("cmdstanpy").setLevel(logging.ERROR)

# Ensure cmdstan is installed at startup
def _ensure_cmdstan_installed():
    """
    Verify that cmdstan is properly installed. If not, install it.
    Called once at module load time.
    """
    try:
        from cmdstanpy.utils.cmdstan import CMDSTAN_PATH
        if CMDSTAN_PATH and os.path.exists(CMDSTAN_PATH):
            makefile_path = os.path.join(CMDSTAN_PATH, "makefile")
            if os.path.exists(makefile_path):
                logger.info(f"✓ CmdStan is properly installed at {CMDSTAN_PATH}")
                return
    except Exception:
        pass
    
    # If we reach here, cmdstan needs to be installed
    logger.warning("CmdStan not properly installed, installing now... (this may take 1-2 minutes)")
    try:
        cmdstanpy.install_cmdstan(overwrite=True, cores=1)
        logger.info("✓ CmdStan installed successfully")
    except Exception as e:
        logger.error(f"Failed to install CmdStan: {e}")
        raise

# Call this at module load time
_ensure_cmdstan_installed()


def _run_prophet(df: pd.DataFrame) -> tuple[float, float]:
    """
    Trains Prophet model and returns (mean_predicted_14d, confidence_score).
    Executed in a thread executor — NEVER call from the event loop directly.

    CmdStan backend is guaranteed to be installed by _ensure_cmdstan_installed().
    """
    try:
        model_kwargs = dict(
            yearly_seasonality=False,
            weekly_seasonality=True,
            daily_seasonality=False,
            interval_width=0.80,
            stan_backend="CMDSTANPY",
        )

        model = Prophet(**model_kwargs)
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

    except (AttributeError, RuntimeError, ValueError, FileNotFoundError) as exc:
        # Catch backend initialization errors
        logger.error(f"Prophet backend failed: {exc}", exc_info=True)
        logger.warning("Falling back to simple mean forecast (confidence=0.5)")
        mean_yhat = float(df["y"].mean()) if not df.empty else 0.0
        return max(0.0, mean_yhat), 0.5


class ForecastingService:


    # ------------------------------------------------------------------
    # Core forecast logic
    # ------------------------------------------------------------------
    async def process_event(self, event_data: dict) -> list[dict]:
        """
        Processes a recipe-cooking event and returns a list of forecast result dicts,
        one per ingredient that had enough historical data.

        New architecture: the backend embeds the 90-day consumption history for each
        component directly in the Kafka event (productHistories field). The service
        relies exclusively on this embedded data — zero HTTP calls.
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

        # productHistories is keyed by productId (int in Java → string in JSON)
        embedded_histories: dict = event_data.get("productHistories") or {}
        if embedded_histories:
            logger.info(
                f"Using embedded history for {len(embedded_histories)} product(s) "
                f"(recipe {event_data.get('recipeId')}) — no HTTP calls needed"
            )

        results = []
        loop = asyncio.get_running_loop()

        for comp in components:
            p_id = comp.get("productId")
            if not p_id:
                continue

            # Resolve history from embedded data (zero HTTP)
            embedded = embedded_histories.get(str(p_id)) or embedded_histories.get(p_id)
            if not embedded:
                logger.warning(f"No history for product {p_id} (missing in event) — skipping")
                continue
            
            breakdown = embedded   # list of {date, consumed}

            # ── Build DataFrame ───────────────────────────────────────────
            # Java/Jackson might send dates as [YYYY, MM, DD]. Convert to string if so.
            for entry in breakdown:
                d = entry.get("date")
                if isinstance(d, list) and len(d) >= 3:
                    entry["date"] = f"{d[0]}-{d[1]:02d}-{d[2]:02d}"

            df = pd.DataFrame(breakdown)

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
