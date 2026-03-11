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
_EXECUTOR = None

logging.getLogger("prophet").setLevel(logging.ERROR)
logging.getLogger("cmdstanpy").setLevel(logging.ERROR)

def _ensure_cmdstan_installed():
    import pathlib
    import re
    import prophet as _prophet

    # Discover the EXACT version Prophet will look for
    models_file = pathlib.Path(_prophet.__file__).parent / "models.py"
    cmdstan_version = "2.33.1"  # known default for prophet 1.1.5
    try:
        content = models_file.read_text()
        match = re.search(r'cmdstan[_-]version\s*=\s*["\']([\d.]+)["\']', content)
        if not match:
            match = re.search(r'["\']cmdstan-(\d+\.\d+\.\d+)["\']', content)
        if match:
            cmdstan_version = match.group(1)
    except Exception:
        pass

    prophet_stan_dir = pathlib.Path(_prophet.__file__).parent / "stan_model"
    expected_path = prophet_stan_dir / f"cmdstan-{cmdstan_version}"
    makefile = expected_path / "makefile"

    if makefile.exists():
        return

    try:
        prophet_stan_dir.mkdir(parents=True, exist_ok=True)
        cmdstanpy.install_cmdstan(
            dir=str(prophet_stan_dir),
            version=cmdstan_version,
            overwrite=True,
            cores=1,
        )
    except Exception as e:
        logger.error(f"Failed to install CmdStan {cmdstan_version}: {e}")
        raise

_ensure_cmdstan_installed()


def _run_prophet(df: pd.DataFrame) -> tuple[float, float]:
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
    async def process_event(self, event_data: dict) -> list[dict]:
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
                    "eventType": "PREDICTION",
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
