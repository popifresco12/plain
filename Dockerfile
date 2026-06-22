FROM python:3.11-slim

WORKDIR /app

# Copy only backend
COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/ .

EXPOSE 8000

# Shell form para que $PORT se expanda correctamente
CMD uvicorn main:app --host 0.0.0.0 --port $PORT
