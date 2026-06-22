# 🚀 Despliegue a Producción — PLAIN

Guía paso a paso para poner PLAIN en producción con HTTPS, dominio real y Stripe funcional.

---

## 📦 Opción 1 — Railway (recomendada, gratis hasta cierto punto)

[Railway](https://railway.app) tiene integración directa con GitHub y despliegue automático.

### Backend (FastAPI)

```bash
# 1. Sube el código a GitHub (si no está ya)
cd ~/plain
git push origin main

# 2. Ve a https://railway.app → New Project → Deploy from GitHub repo
#    Selecciona popifresco12/plain

# 3. Railway detecta el backend/ automáticamente
#    Configura el Start Command:
cd backend && uvicorn main:app --host 0.0.0.0 --port $PORT

# 4. Añade estas Variables de Entorno:
#    PLAIN_SECRET_KEY=genera_un_token_seguro
#    STRIPE_SECRET_KEY=sk_live_... (de tu dashboard de Stripe)
#    STRIPE_WEBHOOK_SECRET=whsec_... (de Stripe Dashboard → Webhooks)
```

### Variables de Entorno necesarias

| Variable | Obligatoria | Cómo obtenerla |
|----------|:-----------:|----------------|
| `PLAIN_SECRET_KEY` | ✅ | `python3 -c "import secrets; print(secrets.token_hex(32))"` |
| `STRIPE_SECRET_KEY` | ❌ (solo para pagos reales) | [Dashboard Stripe](https://dashboard.stripe.com/apikeys) → Clave secreta |
| `STRIPE_WEBHOOK_SECRET` | ❌ (solo para pagos reales) | Stripe → Webhooks → Añadir endpoint → `https://tudominio.com/api/stripe/webhook` |

### Base de datos

Railway te da una PostgreSQL gratis:
- Railway → New → Database → PostgreSQL
- Copia la `DATABASE_URL`
- Añádela como variable de entorno en el servicio backend

Luego, en `backend/database.py`, cambia la conexión SQLite por PostgreSQL:

```python
import os
DATABASE_URL = os.environ.get("DATABASE_URL", "sqlite:///./plain.db")
```

### Stripe Webhook (para pagos reales)

1. En [Stripe Dashboard](https://dashboard.stripe.com/webhooks) → **Añadir endpoint**
2. URL: `https://tudominio.railway.app/api/stripe/webhook`
3. Eventos a escuchar: `checkout.session.completed`
4. Cópia el **Signing Secret** (`whsec_...`) y ponlo en `STRIPE_WEBHOOK_SECRET`

### Frontend Web (el panel de empresa)

El dashboard web se sirve desde el mismo backend (FastAPI static files).
No necesita dominio aparte — funciona en `https://tudominio.railway.app/business`

---

## 📦 Opción 2 — VPS (DigitalOcean, Hetzner, etc.)

### Requisitos

- Servidor Ubuntu 22.04+ con Docker y Docker Compose
- Dominio configurado (opcional, pero recomendado para HTTPS)

### 1. Docker Compose

Crea `docker-compose.yml` en la raíz de `~/plain/`:

```yaml
version: '3.8'

services:
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "8000:8000"
    environment:
      - PLAIN_SECRET_KEY=${PLAIN_SECRET_KEY}
      - STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY}
      - STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET}
    volumes:
      - ./backend/plain.db:/app/plain.db
    restart: unless-stopped
```

### 2. Dockerfile

Crea `backend/Dockerfile`:

```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY . .
RUN pip install -r requirements.txt
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 3. Proxy inverso con Nginx + Certbot (HTTPS)

```bash
# En el servidor
sudo apt update && sudo apt install nginx certbot python3-certbot-nginx

# Configurar Nginx
sudo nano /etc/nginx/sites-available/plain
```

```nginx
server {
    listen 80;
    server_name plain.tudominio.com;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
# Activar y obtener certificado SSL
sudo ln -s /etc/nginx/sites-available/plain /etc/nginx/sites-enabled/
sudo certbot --nginx -d plain.tudominio.com

# Desplegar
docker compose up -d --build
```

### 4. Stripe Webhook (para HTTPS local)

Para desarrollo local con Stripe, usa Stripe CLI:

```bash
# Instalar Stripe CLI
brew install stripe/stripe-cli/stripe  # macOS
# O descargar de https://stripe.com/docs/stripe-cli

# Forward de webhooks a local
stripe listen --forward-to localhost:8000/api/stripe/webhook

# Te dará un STRIPE_WEBHOOK_SECRET (whsec_...) para usar localmente
```

---

## 📱 App Android

### Conectar al backend desplegado

En `app/src/main/java/com/plain/app/data/ApiClient.kt`:

```kotlin
// En desarrollo local
const val BASE_URL = "http://10.0.2.2:8000/"  // Emulador Android

// En producción
const val BASE_URL = "https://plain.tudominio.com/"
```

Luego genera un nuevo APK de release:

```bash
cd ~/plain
JAVA_HOME=~/jdk17 ANDROID_HOME=~/android ./gradlew assembleRelease
# El APK está en: app/build/outputs/apk/release/app-release.apk
```

---

## ✅ Checklist final de producción

- [ ] `PLAIN_SECRET_KEY` configurada (no la de desarrollo)
- [ ] `STRIPE_SECRET_KEY` configurada (clave **live**, no test)
- [ ] `STRIPE_WEBHOOK_SECRET` configurada
- [ ] Webhook de Stripe apuntando a tu dominio
- [ ] HTTPS funcionando (certificado válido)
- [ ] PostgreSQL en vez de SQLite (opcional pero recomendado)
- [ ] Android APK con `BASE_URL` apuntando al dominio real
- [ ] Tests: `cd backend && source venv/bin/activate && python -m pytest`
- [ ] Push a GitHub para despliegue automático (Railway)
