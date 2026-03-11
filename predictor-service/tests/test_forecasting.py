import pytest
import asyncio
from app.services.forecasting_service import forecast_service

@pytest.mark.asyncio
async def test_process_event_with_array_dates():
    """
    Test that process_event can handle Jackson-style array dates 
    without triggering the Prophet 'stan_backend' AttributeError.
    """
    # Mock event with history in array format [YYYY, MM, DD]
    event = {
        "recipeId": 12,
        "productHistories": {
            "402": [
                {"date": [2026, 3, 2], "consumed": 4.0},
                {"date": [2026, 3, 5], "consumed": 16.0},
                {"date": [2026, 3, 6], "consumed": 7.0},
                {"date": [2026, 3, 7], "consumed": 1.0},
                {"date": [2026, 3, 9], "consumed": 3.0},
                {"date": [2026, 3, 10], "consumed": 21.0}
            ]
        }
    }
    
    # This should run without AttributeError from Prophet and should return at
    # least one forecast entry using the naming from the service implementation.
    results = await forecast_service.process_event(event)
    
    assert results is not None
    assert len(results) > 0
    assert results[0]["productId"] == 402
    assert "projectedConsumption" in results[0]
    assert 0.0 <= results[0]["confidenceScore"] <= 1.0


@pytest.mark.asyncio
async def test_fallback_when_prophet_missing(monkeypatch):
    """Ensure the service does not crash when Prophet complains about
    ``stan_backend`` being unavailable.
    """
    # force the constructor to raise the specific attribute error
    class FakeProphet:
        def __init__(self, *args, **kwargs):
            raise AttributeError("'Prophet' object has no attribute 'stan_backend'")

    monkeypatch.setattr(forecast_service, "Prophet", FakeProphet)

    event = {
        "recipeId": 99,
        "productHistories": {"1": [{"date": [2026, 3, 1], "consumed": 10.0}]},
    }
    results = await forecast_service.process_event(event)
    # fallback should still return a (very basic) prediction entry
    assert len(results) == 1
    assert results[0]["productId"] == 1
    assert "projectedConsumption" in results[0]
    assert results[0]["confidenceScore"] == 0.5
