#!/usr/bin/env bash
# btry 服务器初始化（幂等）。由 tools/deploy-prod.ps1 上传后以 root 执行：
#   sudo bash /tmp/setup-server.sh
# 前置：/opt/btry/.env 已就位，/tmp 下有 btry.jar / btry-web.tar.gz / btry-seed.sql
#       /tmp/btry.service /tmp/nginx-learn.conf
set -euo pipefail

APP_DIR=/opt/btry
ENV_FILE="$APP_DIR/.env"
SEED_SQL=/tmp/btry-seed.sql
WEB_ROOT=/var/www/whereri.site
NGINX_SITE=/etc/nginx/sites-available/whereri.site
SNIPPET=/etc/nginx/snippets/btry-learn.conf
REDIS_CONF=/etc/redis/redis.conf

[ -f "$ENV_FILE" ] || { echo "missing $ENV_FILE" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

mkdir -p "$APP_DIR/logs"
chmod 600 "$ENV_FILE"

echo "==> MySQL"
systemctl enable --now mysql
for _ in $(seq 1 30); do
  mysqladmin ping --silent >/dev/null 2>&1 && break
  sleep 1
done
mysql -e "CREATE DATABASE IF NOT EXISTS btry_edu CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -e "CREATE USER IF NOT EXISTS 'btry'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';"
mysql -e "ALTER USER 'btry'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';"
mysql -e "GRANT ALL PRIVILEGES ON btry_edu.* TO 'btry'@'localhost'; FLUSH PRIVILEGES;"

TABLES=$(mysql -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='btry_edu';")
if [ "$TABLES" = "0" ] && [ -f "$SEED_SQL" ]; then
  mysql btry_edu < "$SEED_SQL"
  echo "==> seed imported"
else
  echo "==> seed skipped (tables=$TABLES)"
fi

echo "==> Redis"
if grep -q "^requirepass " "$REDIS_CONF"; then
  sed -i "s/^requirepass .*/requirepass ${REDIS_PASSWORD}/" "$REDIS_CONF"
else
  sed -i "s/^# requirepass foobared/requirepass ${REDIS_PASSWORD}/" "$REDIS_CONF"
  grep -q "^requirepass " "$REDIS_CONF" || echo "requirepass ${REDIS_PASSWORD}" >> "$REDIS_CONF"
fi
sed -i "s/^bind 127.0.0.1 ::1/bind 127.0.0.1/" "$REDIS_CONF"
systemctl enable --now redis-server
systemctl restart redis-server

echo "==> systemd"
cp /tmp/btry.service /etc/systemd/system/btry.service
systemctl daemon-reload
systemctl enable btry.service

echo "==> nginx"
mkdir -p /etc/nginx/snippets
cp /tmp/nginx-learn.conf "$SNIPPET"
if ! grep -q "btry-learn.conf" "$NGINX_SITE"; then
  cp "$NGINX_SITE" "${NGINX_SITE}.bak.$(date +%Y%m%d%H%M%S)"
  sed -i '/index index.html;/a\    include /etc/nginx/snippets/btry-learn.conf;' "$NGINX_SITE"
fi
nginx -t
systemctl reload nginx

echo "==> web"
mkdir -p "$WEB_ROOT/learn"
rm -rf "$WEB_ROOT/learn/assets" "$WEB_ROOT/learn/index.html"
tar -xzf /tmp/btry-web.tar.gz -C "$WEB_ROOT/learn"
chown -R www-data:www-data "$WEB_ROOT/learn"
chown -R ubuntu:ubuntu "$APP_DIR"

echo "==> start btry"
systemctl restart btry.service
sleep 3
systemctl is-active btry.service
echo "setup done"
