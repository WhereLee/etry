#!/usr/bin/env bash
set -euo pipefail

cd /opt/etry
COMPOSE=(sudo -n docker compose --env-file .env.docker -f compose.yml -f compose.server.yml)
WEBROOT=/var/www/whereri.site
PREV_WEB="$(ls -1dt "$WEBROOT"/learn.prev-* 2>/dev/null | head -1)"
PREV_IMAGE="$(sudo -n docker images btry-backend --format '{{.Tag}}' | grep '^prev-' | head -1)"
CUR_WEB="$WEBROOT/learn.drill-current"
WEB_SWAPPED=0

wait_ready() {
  for _ in $(seq 1 60); do
    if sudo -n docker exec btry-backend-1 wget -qO- http://127.0.0.1:9091/actuator/health/readiness 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done
  echo "readiness timeout" >&2
  return 1
}

login_has_roles() {
  local password
  password="$(sudo -n grep '^SEED_ADMIN_PASSWORD=' .env.docker | cut -d= -f2-)"
  curl -s -X POST https://whereri.site/api/auth/login \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"admin\",\"password\":\"$password\"}" | grep -q '"roles"'
}

restore() {
  set +e
  if [ "$WEB_SWAPPED" = "1" ]; then
    if [ -d "$WEBROOT/learn" ] && [ ! -d "$PREV_WEB" ]; then
      sudo -n mv "$WEBROOT/learn" "$PREV_WEB"
    fi
    if [ -d "$CUR_WEB" ]; then
      sudo -n mv "$CUR_WEB" "$WEBROOT/learn"
    fi
  fi
  sudo -n docker tag btry-backend:drill-current btry-backend:latest
  "${COMPOSE[@]}" up -d backend >/dev/null
  wait_ready
  sudo -n docker tag -f btry-backend:drill-current btry-backend:latest >/dev/null 2>&1 || true
  echo "RESTORED readiness=$?"
}
trap restore EXIT

echo "== current =="
sudo -n docker tag btry-backend:latest btry-backend:drill-current
echo "current_image=$(sudo -n docker inspect btry-backend:latest --format '{{.Id}}' | head -c 12)"
echo "prev_image=$PREV_IMAGE web_prev=$PREV_WEB"
login_has_roles && echo "current_login_roles=yes" || echo "current_login_roles=no"
curl -s -o /dev/null -w "current_health=%{http_code}\n" https://whereri.site/api/health

echo "== rollback backend =="
sudo -n docker tag "btry-backend:$PREV_IMAGE" btry-backend:latest
"${COMPOSE[@]}" up -d backend >/dev/null
wait_ready
login_has_roles && echo "rollback_login_roles=yes" || echo "rollback_login_roles=no"
curl -s -o /dev/null -w "rollback_health=%{http_code}\n" https://whereri.site/api/health

echo "== rollback frontend =="
if [ -n "$PREV_WEB" ] && [ -d "$PREV_WEB" ]; then
  CUR_ASSET="$(curl -s https://whereri.site/learn/ | grep -o 'assets/index-[^"]*\.js' | head -1)"
  sudo -n mv "$WEBROOT/learn" "$CUR_WEB"
  sudo -n mv "$PREV_WEB" "$WEBROOT/learn"
  WEB_SWAPPED=1
  OLD_ASSET="$(curl -s https://whereri.site/learn/ | grep -o 'assets/index-[^"]*\.js' | head -1)"
  echo "current_asset=$CUR_ASSET rollback_asset=$OLD_ASSET"
  [ "$CUR_ASSET" != "$OLD_ASSET" ] && echo "frontend_asset_changed=yes" || echo "frontend_asset_changed=no"
  curl -s -o /dev/null -w "rollback_web=%{http_code}\n" https://whereri.site/learn/
else
  echo "skip frontend rollback: no previous webroot"
fi

echo "== restore =="
exit 0
