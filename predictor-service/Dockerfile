FROM mirror.gcr.io/library/python:3.11-slim

# Metadata
LABEL maintainer="economato" \
      service="predictor-service"

WORKDIR /app

# Install OS build deps and curl (for healthcheck), then clean up
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        python3-dev \
        gcc \
        curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python deps before copying source (better layer caching)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY . .

# ── Security: run as non-root ──────────────────────────────────────────
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

EXPOSE 8000

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

# Use exec form (no shell) so signals reach uvicorn directly
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
