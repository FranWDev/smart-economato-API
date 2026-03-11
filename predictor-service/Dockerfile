FROM mirror.gcr.io/library/python:3.11-slim

LABEL maintainer="economato" \
      service="predictor-service"

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        python3-dev \
        gcc \
        curl \
        make \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system appgroup \
    && adduser --system --ingroup appgroup --home /home/appuser appuser \
    && chown -R appuser:appgroup /app /home/appuser

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Install cmdstan into the exact location Prophet expects:
# prophet/models.py hardcodes 'stan_model/cmdstan-{version}' inside the package directory.
# We install as root so site-packages is writable, then lock down permissions.
RUN python -c "
import cmdstanpy, pathlib, prophet
# Prophet hardcodes this path in its models.py
prophet_stan_dir = pathlib.Path(prophet.__file__).parent / 'stan_model'
prophet_stan_dir.mkdir(parents=True, exist_ok=True)
print(f'Installing cmdstan into {prophet_stan_dir}')
cmdstanpy.install_cmdstan(dir=str(prophet_stan_dir), overwrite=True, cores=1)
print('cmdstan installed successfully')
"

USER appuser

COPY --chown=appuser:appgroup . .

EXPOSE 8000

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]