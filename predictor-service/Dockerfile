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
    && mkdir -p /app/data \
    && chown -R appuser:appgroup /app /home/appuser /app/data

COPY requirements.txt install_cmdstan.py ./
RUN pip install --no-cache-dir -r requirements.txt

# Install cmdstan into the exact location Prophet expects:
# prophet/models.py hardcodes 'stan_model/cmdstan-{version}' inside the package directory.
# Run as root so site-packages is writable.
RUN python install_cmdstan.py

USER appuser

COPY --chown=appuser:appgroup . .

EXPOSE 8000

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]