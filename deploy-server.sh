#!/usr/bin/env bash
set -euo pipefail

cd /opt/etry

COMPOSE=(sudo -n docker compose --env-file .env.docker -f compose.yml -f compose.server.yml)
NODE_IMAGE=node:20-alpine
NPM_REGISTRY=https://registry.npmmirror.com
TS="$(date +%Y%m%d-%H%M%S)"

if sudo -n docker image inspect btry-backend >/dev/null 2>&1; then
  sudo -n docker tag btry-backend "btry-backend:prev-$TS"
fi

echo "==> build backend image"
"${COMPOSE[@]}" build backend

echo "==> build frontend (base=/learn/)"
sudo -n docker run --rm \
  -v /opt/etry/web:/app -w /app \
  -e NPM_CONFIG_REGISTRY="$NPM_REGISTRY" \
  "$NODE_IMAGE" sh -c "npm ci --no-audit --no-fund && npm run build -- --base=/learn/"

echo "==> switch frontend (atomic)"
WEBROOT=/var/www/whereri.site
sudo -n rm -rf "$WEBROOT/learn.new"
sudo -n cp -a /opt/etry/web/dist "$WEBROOT/learn.new"
sudo -n chown -R www-data:www-data "$WEBROOT/learn.new"
if [ -d "$WEBROOT/learn" ]; then
  sudo -n mv "$WEBROOT/learn" "$WEBROOT/learn.prev-$TS"
fi
sudo -n mv "$WEBROOT/learn.new" "$WEBROOT/learn"
ls -1dt "$WEBROOT"/learn.prev-* 2>/dev/null | tail -n +3 | xargs -r sudo -n rm -rf || true

echo "==> start containers"
"${COMPOSE[@]}" up -d mysql redis backend

echo "==> wait readiness (inside container; management stays loopback)"
for _ in $(seq 1 90); do
  if sudo -n docker exec btry-backend-1 wget -qO- http://127.0.0.1:9091/actuator/health/readiness 2>/dev/null | grep -q '"status":"UP"'; then
    echo "READY"
    "${COMPOSE[@]}" ps
    exit 0
  fi
  sleep 2
done

echo "ERROR: readiness not reached" >&2
"${COMPOSE[@]}" ps >&2 || true
"${COMPOSE[@]}" logs --tail=120 backend >&2 || true
exit 1
