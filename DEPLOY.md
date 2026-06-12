# PLAIN — Deployment Guide

## 🚀 Quick Deploy (Render)

1. Push the repo to GitHub
2. Go to [render.com](https://render.com) → New Web Service
3. Connect your GitHub repo
4. Settings:
   - **Name**: `plain-api`
   - **Root Directory**: `backend`
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `uvicorn main:app --host 0.0.0.0 --port 8000`
   - **Plan**: Free

5. **Environment Variables** (required):
   ```
   PLAIN_SECRET_KEY=<genera una clave: python3 -c "import os; print(os.urandom(32).hex())">
   ```

## 🔒 HTTPS

### Option A: Render (auto)
Render provides **auto HTTPS** on all `*.onrender.com` URLs and custom domains. Zero config.

### Option B: Self-hosted with Caddy (free, automatic Let's Encrypt)

```bash
# Install Caddy
sudo apt install caddy

# Caddyfile
plain.tudominio.com {
    reverse_proxy localhost:8000
}

# Start
sudo systemctl enable caddy
sudo systemctl start caddy
```

### Option C: Nginx + Certbot

```bash
sudo apt install nginx certbot python3-certbot-nginx

# /etc/nginx/sites-available/plain
server {
    listen 80;
    server_name plain.tudominio.com;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

sudo certbot --nginx -d plain.tudominio.com
```

## 🔐 Security

| Variable | Required | Description |
|----------|----------|-------------|
| `PLAIN_SECRET_KEY` | ✅ Yes (prod) | 64-char hex key for JWT signing |

Generate a key:
```bash
python3 -c "import os; print(os.urandom(32).hex())"
```

## 🛠️ Development

```bash
# Backend
cd backend
uvicorn main:app --reload --port 8000

# Android (emulator)
cd ..
JAVA_HOME=~/jdk17 ANDROID_HOME=~/android ./gradlew assembleDebug
```

## 📱 Release Build

```bash
./build-release.sh
# Output: app/build/outputs/apk/release/app-release.apk
```

> ⚠️ The keystore (`plain-release-key.jks`) is in the repo root. **Keep it safe** — you need it for future updates. Password: `PlainRelease1`
