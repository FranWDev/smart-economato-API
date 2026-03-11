#!/usr/bin/env python3
"""
Test script to validate Prophet backend configuration and initialization.
Run this locally or in the Docker container to diagnose backend issues.
"""
import logging
import os
import sys
import pandas as pd
from datetime import datetime, timezone

# Set up logging to see debug output
logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)

logger = logging.getLogger(__name__)

def test_cmdstan_path():
    """Check if cmdstan is installed at expected location."""
    cmdstan_path = os.getenv("CMDSTAN_PATH", "/home/appuser/.cmdstan")
    print(f"\n1. Checking cmdstan path: {cmdstan_path}")
    if os.path.exists(cmdstan_path):
        print(f"   ✓ Path exists")
        if os.path.isdir(cmdstan_path):
            print(f"   ✓ Is a directory")
            files = os.listdir(cmdstan_path)
            print(f"   ✓ Contains {len(files)} files/folders: {files[:3]}...")
        return True
    else:
        print(f"   ✗ Path does NOT exist")
        return False


def test_cmdstanpy_import():
    """Test if cmdstanpy can be imported."""
    print(f"\n2. Testing cmdstanpy import...")
    try:
        import cmdstanpy
        print(f"   ✓ cmdstanpy imported successfully")
        print(f"   ✓ Version: {cmdstanpy.__version__}")
        return True
    except ImportError as e:
        print(f"   ✗ Failed to import cmdstanpy: {e}")
        return False


def test_prophet_backend():
    """Test Prophet with explicit cmdstanpy backend."""
    print(f"\n3. Testing Prophet with CMDSTANPY backend...")
    
    try:
        from prophet import Prophet
        
        # Prepare minimal test data
        df = pd.DataFrame({
            'ds': pd.date_range('2024-01-01', periods=30),
            'y': [10 + i % 5 for i in range(30)]
        })
        
        # Set environment variables
        cmdstan_path = os.getenv("CMDSTAN_PATH", "/home/appuser/.cmdstan")
        os.environ["STAN_BACKEND"] = "CMDSTANPY"
        os.environ["CMDSTAN"] = cmdstan_path
        
        logger.info(f"Initializing Prophet with stan_backend=CMDSTANPY, CMDSTAN={cmdstan_path}")
        
        # Create model with explicit backend
        model = Prophet(
            stan_backend="CMDSTANPY",
            yearly_seasonality=False,
            weekly_seasonality=False,
            daily_seasonality=False,
            interval_width=0.80,
        )
        
        print(f"   ✓ Prophet model created")
        
        # Fit model
        logger.info("Fitting Prophet model (this may take 30-60 seconds)...")
        with suppress_cmdstanpy_output():
            model.fit(df)
        
        print(f"   ✓ Model fitted successfully")
        
        # Make forecast
        future = model.make_future_dataframe(periods=7)
        forecast = model.predict(future)
        
        print(f"   ✓ Forecast generated")
        print(f"   ✓ Last 7 days prediction: {forecast[['ds', 'yhat']].tail(7).to_string()}")
        
        return True
        
    except Exception as e:
        print(f"   ✗ Prophet backend test failed: {e}")
        import traceback
        traceback.print_exc()
        return False


class suppress_cmdstanpy_output:
    """Context manager to suppress verbose cmdstanpy output."""
    def __enter__(self):
        self._stdout = sys.stdout
        self._stderr = sys.stderr
        sys.stdout = open(os.devnull, 'w')
        sys.stderr = open(os.devnull, 'w')
        return self
    
    def __exit__(self, *args):
        sys.stdout.close()
        sys.stderr.close()
        sys.stdout = self._stdout
        sys.stderr = self._stderr


def main():
    print("=" * 70)
    print("Prophet Backend Diagnostic Test")
    print("=" * 70)
    
    results = {
        "cmdstan_path": test_cmdstan_path(),
        "cmdstanpy_import": test_cmdstanpy_import(),
        "prophet_backend": test_prophet_backend(),
    }
    
    print("\n" + "=" * 70)
    print("Test Results Summary")
    print("=" * 70)
    for test_name, passed in results.items():
        status = "✓ PASS" if passed else "✗ FAIL"
        print(f"{status}: {test_name}")
    
    all_passed = all(results.values())
    print("=" * 70)
    if all_passed:
        print("✓ All tests passed! Prophet backend is properly configured.")
        return 0
    else:
        print("✗ Some tests failed. Check the output above for details.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
