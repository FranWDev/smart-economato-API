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
        """
        Processes a StockPredictionEvent from the unifed 'stock-prediction-events' topic.
        """
        trigger_type = event_data.get("triggerType", "UNKNOWN")
        affected_product_ids = event_data.get("affectedProductIds", [])
        embedded_histories = event_data.get("productHistories") or {}

        if not affected_product_ids:
            logger.warning(f"No affected product IDs found in event {trigger_type}")
            return []

        logger.info(
            f"Processing {trigger_type} event: {len(affected_product_ids)} products affected. "
            f"{len(embedded_histories)} histories provided."
        )

        results = []
        for p_id_str, history in embedded_histories.items():
            try:
                p_id = int(p_id_str)
                result = await self._forecast_product(p_id, history)
                if result:
                    results.append(result)
            except Exception as exc:
                logger.error(f"Error forecasting product {p_id_str}: {exc}", exc_info=True)

        return results

    async def _forecast_product(self, product_id: int, history: list[dict]) -> dict | None:
        """
        Runs the forecasting logic for a single product with zero-fill data augmentation.
        """
        if not history:
            logger.warning(f"Empty history for product {product_id} — skipping")
            return None

        # ── Build DataFrame ───────────────────────────────────────────
        for entry in history:
            d = entry.get("date")
            if isinstance(d, list) and len(d) >= 3:
                entry["date"] = f"{d[0]}-{d[1]:02d}-{d[2]:02d}"

        df = pd.DataFrame(history)
        if "date" not in df.columns or "consumed" not in df.columns:
            logger.error(f"Unexpected history schema for product {product_id}: {df.columns.tolist()}")
            return None

        df = df.rename(columns={"date": "ds", "consumed": "y"})
        df["ds"] = pd.to_datetime(df["ds"], errors="coerce")
        df["y"] = pd.to_numeric(df["y"], errors="coerce")
        df = df.dropna(subset=["ds", "y"])

        if len(df) < 2:
            logger.warning(f"Not enough clean data points for product {product_id} ({len(df)}) — skipping")
            return None

        # ── Zero-Fill Logic ──────────────────────────────────────────
        # Prophet works significantly better if we explicitly tell it about "zero consumption" days.
        # Otherwise, it might assume missing days are unknown, rather than zero.
        try:
            df = df.set_index("ds").sort_index()
            # We cover the last 90 days or the range of data we have
            min_date = df.index.min()
            max_date = datetime.now().date()
            
            full_range = pd.date_range(start=min_date, end=max_date, freq="D")
            df = df.reindex(full_range, fill_value=0.0)
            df.index.name = "ds"
            df = df.reset_index()
        except Exception as e:
            logger.error(f"Zero-fill failed for product {product_id}: {e}")
            # Continue with original df if reindexing fails
            df = df.reset_index()

        loop = asyncio.get_running_loop()
        try:
            prediction, confidence = await loop.run_in_executor(
                _EXECUTOR, _run_prophet, df
            )

            return {
                "productId": product_id,
                "projectedConsumption": round(prediction, 2),
                "calculatedAt": datetime.now(tz=timezone.utc).isoformat(),
                "modelUsed": "Meta Prophet v1.1 (Zero-Filled)",
                "confidenceScore": confidence,
                "eventType": "PREDICTION",
                "forecastHorizonDays": 14,
            }
        except Exception as exc:
            logger.error(f"Prophet failed for product {product_id}: {exc}", exc_info=True)
            return None


forecast_service = ForecastingService()
