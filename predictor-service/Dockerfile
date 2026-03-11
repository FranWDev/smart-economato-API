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

USER appuser
# Install cmdstan in the standard location where Prophet expects it
# Use --cores 1 to avoid memory issues in constrained environments
RUN python -m cmdstanpy.install_cmdstan --cores 1

COPY --chown=appuser:appgroup . .

EXPOSE 8000

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]