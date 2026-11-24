# 服务器部署说明（/opt/etry）

## 架构
- Docker：MySQL 8、Redis 7、后端 Spring Boot（`compose.yml` + `compose.server.yml`，prod profile）。
- 前端：服务器源码构建（Vite base=/learn/），由宿主 nginx 从 `/var/www/whereri.site/learn/` 提供。
- 入口：`https://whereri.site/learn/`；API：`/api/` → `127.0.0.1:8084`。
- 数据库：容器内全新库（Flyway V1–V24），与旧宿主 `btry_edu`（V13，已停止）无关。
- 管理端口 9091 仅在容器内 127.0.0.1 监听，不发布到宿主；健康检查用 `docker exec`。
- 密钥：`/opt/etry/.env.docker`（600）。

## 日常发布
```bash
cd /opt/etry
bash deploy-server.sh
```
脚本会：构建后端镜像 → 用 node 容器构建前端 → 原子切换 `learn` 目录 → 启动/更新容器 → 容器内 readiness 检查。

## 健康与日志
```bash
sudo docker compose --env-file .env.docker -f compose.yml -f compose.server.yml ps
sudo docker exec btry-backend-1 wget -qO- http://127.0.0.1:9091/actuator/health/readiness
curl -fsS https://whereri.site/api/health
sudo docker compose --env-file .env.docker -f compose.yml -f compose.server.yml logs -f backend
```

## 自动备份
- 每日 03:30（persistent），`btry-container-backup.timer` → `btry-container-backup.service`。
- 内容：容器 MySQL 全量 dump + `btry_material-data` 资料卷；上传 COS `backups/btry-container-daily/<时间>/`；本地保留 14 天。
- 手动执行/排查：
```bash
sudo systemctl start btry-container-backup.service
journalctl -u btry-container-backup -n 50
ls -lh /opt/etry/backups/
```

## 回滚
- 前端：`/var/www/whereri.site/learn.prev-*`（保留最近 2 份），改回目录名即可。
- 后端镜像：构建前自动打 `btry-backend:prev-<时间>`；回滚：
  `sudo docker tag btry-backend:prev-<时间> btry-backend`，再 `compose up -d backend`。
- 旧课程版：jar 与 V13 数据库备份在 COS `backups/etry-maintenance-2026-11-23/`。

## 紧急数据库备份（手动）
```bash
sudo docker exec btry-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -ubtry --single-transaction btry_edu' | gzip > btry_edu-$(date +%F).sql.gz
```
