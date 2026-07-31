FROM python:3.11-slim

WORKDIR /app

# Copy only backend
COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/ .

# Seguridad: usuario sin privilegios (no root)
RUN useradd --create-home --uid 10001 appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

# Healthcheck para que el orquestador detecte caídas
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health', timeout=3)" || exit 1

# Shell form para que $PORT se expanda correctamente
CMD uvicorn main:app --host 0.0.0.0 --port $PORT
