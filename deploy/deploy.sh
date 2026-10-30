#!/usr/bin/env bash
# etry 部署脚本（在服务器上执行）
# 用法：先把 etry.jar、etry-a.service、etry-b.service、nginx-etry.conf 传到 /tmp，
# 再执行本脚本。.env 需要提前放好，脚本只检查不覆盖。
set -euo pipefail

APP_DIR=/opt/etry
STAGE=/tmp

echo "==> 准备目录 $APP_DIR"
sudo mkdir -p "$APP_DIR/logs"
sudo chown -R ubuntu:ubuntu "$APP_DIR"

if [ ! -f "$APP_DIR/.env" ]; then
    echo "!! 缺少 $APP_DIR/.env，请先按 .env.example 填好再执行" >&2
    exit 1
fi
chmod 600 "$APP_DIR/.env"

echo "==> 安装 jar"
install -m 644 "$STAGE/etry.jar" "$APP_DIR/etry.jar"

echo "==> 安装 systemd 单元"
sudo install -m 644 "$STAGE/etry-a.service" /etc/systemd/system/etry-a.service
sudo install -m 644 "$STAGE/etry-b.service" /etc/systemd/system/etry-b.service
sudo systemctl daemon-reload
sudo systemctl enable etry-a etry-b
sudo systemctl restart etry-a etry-b

echo "==> 安装 nginx 站点"
sudo install -m 644 "$STAGE/nginx-etry.conf" /etc/nginx/sites-available/etry
sudo ln -sf /etc/nginx/sites-available/etry /etc/nginx/sites-enabled/etry

# 只留 etry；其余站点的后端已经停了，留着只会在 80 上返回 502。
# 只删 sites-enabled 里的软链，sites-available 里的文件保留，随时可以恢复。
echo "==> 停用其它站点"
for site in default dsh-home filebox-8443 modforge; do
    if [ -L "/etc/nginx/sites-enabled/$site" ]; then
        sudo rm -f "/etc/nginx/sites-enabled/$site"
        echo "  disabled $site"
    fi
done

sudo nginx -t
sudo systemctl enable nginx
sudo systemctl restart nginx

echo "==> 健康检查"
sleep 8
for port in 8081 8082; do
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/health" || true)
    echo "  instance :$port -> HTTP $code"
done
echo "  通过 nginx :80 -> HTTP $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/api/health || true)"

echo "==> 完成。查看日志：sudo journalctl -u etry-a -f"
