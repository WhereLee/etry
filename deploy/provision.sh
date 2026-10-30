#!/usr/bin/env bash
# 首次部署时在服务器上执行：启动依赖、建库建用户、生成 /opt/etry/.env
# 重复执行是安全的：库和用户用 IF NOT EXISTS，.env 会被覆盖（密码重新生成）。
set -euo pipefail

APP_DIR=/opt/etry
DB_NAME=etry
DB_USER=etry
DB_PASS=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)

# 邮箱授权码不在仓库里出现，部署时放在临时文件里，读完即删
MAIL_PASS_FILE=/tmp/etry-mail-pass
if [ ! -f "$MAIL_PASS_FILE" ]; then
    echo "!! 缺少 $MAIL_PASS_FILE（里面放 QQ 邮箱授权码）" >&2
    exit 1
fi
MAIL_PASSWORD=$(tr -d '\r\n' < "$MAIL_PASS_FILE")
rm -f "$MAIL_PASS_FILE"

echo "==> 启动 MySQL"
sudo systemctl enable --now mysql
for i in $(seq 1 30); do
    if mysqladmin ping --silent >/dev/null 2>&1; then break; fi
    sleep 1
done

echo "==> 建库与账号"
sudo mysql <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

echo "==> 启动 Redis"
sudo systemctl enable --now redis-server
sleep 2
# 本机 Redis 开了 requirepass，密码直接从配置里取
REDIS_PASS=$(sudo grep -E '^requirepass' /etc/redis/redis.conf 2>/dev/null | head -1 \
    | sed 's/^requirepass[[:space:]]*//' | tr -d '"' | tr -d '\r' || true)
if [ -n "$REDIS_PASS" ]; then
    echo "  redis password found"
else
    echo "  redis has no password"
fi

echo "==> 读取现有 COS 凭据"
conf=$HOME/.cos.conf
if [ ! -f "$conf" ]; then
    echo "!! 找不到 $conf" >&2
    exit 1
fi
cos_id=$(grep -E '^secret_id' "$conf" | head -1 | cut -d'=' -f2- | tr -d ' \r')
cos_key=$(grep -E '^secret_key' "$conf" | head -1 | cut -d'=' -f2- | tr -d ' \r')
if [ -z "$cos_id" ] || [ -z "$cos_key" ]; then
    echo "!! COS 凭据为空" >&2
    exit 1
fi

echo "==> 写入 $APP_DIR/.env"
sudo mkdir -p "$APP_DIR/logs"
sudo chown -R ubuntu:ubuntu "$APP_DIR"
cat > "$APP_DIR/.env" <<EOF
SPRING_PROFILES_ACTIVE=prod
ETRY_DB_URL="jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
ETRY_DB_USER=${DB_USER}
ETRY_DB_PASSWORD=${DB_PASS}
ETRY_REDIS_HOST=127.0.0.1
ETRY_REDIS_PORT=6379
ETRY_REDIS_PASSWORD=${REDIS_PASS}
ETRY_JWT_SECRET=${JWT_SECRET}
ETRY_COS_SECRET_ID=${cos_id}
ETRY_COS_SECRET_KEY=${cos_key}
ETRY_COS_BUCKET=rag-1325750042
ETRY_COS_REGION=ap-shanghai
ETRY_MAIL_TYPE=smtp
ETRY_MAIL_USERNAME=2357617275@qq.com
ETRY_MAIL_PASSWORD=${MAIL_PASSWORD}
# 注册是否需要邮箱验证。改成 false 则注册即生效、不发信；改完要 systemctl restart etry-a etry-b
ETRY_VERIFY_REGISTER=${VERIFY_REGISTER:-true}
ETRY_STORAGE_TYPE=cos
ETRY_CACHE_TYPE=redis
EOF
chmod 600 "$APP_DIR/.env"

echo "==> 完成，.env 已生成（600）"
grep -c '=' "$APP_DIR/.env"
