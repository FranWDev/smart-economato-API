import pytest
import asyncio
from app.services.forecasting_service import forecast_service

@pytest.mark.asyncio
async def test_process_event_with_array_dates():
    """
    Test that process_event can handle Jackson-style array dates 
    in the new unified StockPredictionEvent format.
    """
    # Mock event with history in array format [YYYY, MM, DD]
    event = {
        "triggerType": "COOK_RECIPE",
        "affectedProductIds": [402],
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
    
    results = await forecast_service.process_event(event)
    
    assert results is not None
    assert len(results) > 0
    assert results[0]["productId"] == 402
    assert "projectedConsumption" in results[0]
    assert "Meta Prophet v1.1 (Zero-Filled)" in results[0]["modelUsed"]
    assert 0.0 <= results[0]["confidenceScore"] <= 1.0
    assert results[0]["eventType"] == "PREDICTION"
