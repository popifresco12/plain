#!/bin/bash
# Despliega el backend en Render y espera a que producción sirva el commit actual.
# Uso: ./deploy-backend.sh
set -euo pipefail
SERVICE="srv-da60sfajobas7386h0l0"
API="https://api.render.com/v1/services/$SERVICE"

if [ -z "${RENDER_API_KEY:-}" ]; then
  echo "❌ Falta RENDER_API_KEY en el entorno."
  exit 1
fi

LOCAL_COMMIT=$(git rev-parse --short=8 HEAD)
echo "▶ Commit local: $LOCAL_COMMIT"
echo "▶ Producción antes: $(curl -s https://plain-api.onrender.com/health)"

echo "▶ Lanzando deploy…"
curl -fsS -X POST -H "Authorization: Bearer $RENDER_API_KEY" -H "Content-Type: application/json" \
  -d '{"clearCache":"do_not_clear"}' "$API/deploys" > /dev/null

for i in $(seq 1 40); do
  sleep 15
  state=$(curl -s -H "Authorization: Bearer $RENDER_API_KEY" "$API/deploys?limit=1" \
    | python -c "import json,sys;print(json.load(sys.stdin)[0]['deploy']['status'])")
  echo "   estado: $state"
  case "$state" in
    live) break ;;
    build_failed|update_failed|pre_deploy_failed|canceled) echo "❌ Deploy fallido"; exit 1 ;;
  esac
done

echo "▶ Producción ahora: $(curl -s https://plain-api.onrender.com/health)"
