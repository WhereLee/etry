# Docker 运行与云服务器验证

更新日期：2026-11-05

## 1. 定位与边界

Docker 只用于本地、演示和云服务器 staging 验证，不替代当前腾讯云单机的 systemd 生产部署。

当前生产入口保持不变：

- systemd：`btry.service`
- 后端端口：`8084`
- Actuator：`9091`
- 公网入口：nginx `80/443`

Compose staging 使用独立端口：

- 前端：`18080`
- 后端：`18084`（仅绑定 `127.0.0.1`）
- Actuator：`19091`（仅绑定 `127.0.0.1`）
- MySQL 测试端口：`13306`（仅绑定 `127.0.0.1`）
- Redis 测试端口：`16379`（仅绑定 `127.0.0.1`）

可选观测栈：

- Prometheus：`127.0.0.1:19090`
- Grafana：`127.0.0.1:13000`
- Jaeger UI：`127.0.0.1:16686`
- Jaeger OTLP：`127.0.0.1:14318`

不要在生产服务器上把 Compose 的 `18084/19091` 改成 `8084/9091`，否则会与 systemd 应用争用端口。

本次改造没有引入 CDN、HLS、MQ、完整 RBAC 或微服务拆分，视频仍然由浏览器直接访问 COS。

## 2. 文件

- `server/Dockerfile`：Maven 多阶段构建，运行镜像为 `eclipse-temurin:17-jdk-alpine`，以非 root 用户运行，保留 `jcmd/jstat/jstack`。
- `server/docker/maven-settings.xml`：Docker 构建专用阿里云 Maven 镜像；海外环境可删除或替换。
- `web/Dockerfile`：Node 20 构建 Vue，nginx 1.27 托管静态文件。
- `web/nginx.conf`：SPA 回退、`/api` 反向代理、gzip、静态资源缓存、4GB 上传上限。
- `compose.yml`：MySQL、Redis、后端、前端，含健康检查、日志轮转、内存上限和回环端口。
- `compose.observability.yml`：Prometheus、Grafana、Jaeger 可选 profile。
- `observability/`：Prometheus 抓取配置、Grafana provisioning 和 dashboard。
- `tools/test-data/`：SQL/EXPLAIN 性能数据和查询脚本。
- `tools/k6/`：smoke/load 脚本、LOAD 系列 seed/cleanup。
- `.env.docker.example`：本地/staging 环境变量模板。

Docker 镜像只从环境变量读取数据库、Redis、JWT、COS 和 tracing 配置，不把 `.env.docker` 打进镜像。

## 3. 本地使用

```powershell
Copy-Item .env.docker.example .env.docker
# 填写 .env.docker 中的数据库、Redis、JWT、COS 配置
docker compose --env-file .env.docker up --build
```

访问 `http://127.0.0.1:8080/`。

默认情况下后端、Actuator、MySQL、Redis 测试端口都只绑定本机回环，外部只能通过 nginx 的 `/api` 访问。确实需要从其他机器直连时，显式覆盖：

```text
BACKEND_BIND=0.0.0.0
MANAGEMENT_BIND=0.0.0.0
MYSQL_BIND=0.0.0.0
REDIS_BIND=0.0.0.0
```

Compose 使用 Spring 默认 profile，支付渠道为 `mock`。`prod` profile 会由 `PaymentSafetyGuard` 主动拒绝 Mock 支付，这是刻意的安全边界。

首次启动会创建 `admin / teacher / student` 三个演示账号：

- 推荐在 `.env.docker` 设置 `SEED_ADMIN_PASSWORD`、`SEED_TEACHER_PASSWORD`、`SEED_STUDENT_PASSWORD`。
- 不设置时，启动日志会打印一次随机初始密码。
- 三个账号首次登录都强制改密。

## 4. 云服务器 staging

本次验证使用云服务器目录：

```text
/home/ubuntu/btry-staging
```

`.env.docker` 权限必须为 `600`，COS 凭据从生产 `/opt/btry/.env` 读取后写入 staging 环境文件，未写入仓库，也未在终端回显。

常用命令：

```bash
cd /home/ubuntu/btry-staging

# 状态
sudo docker compose --env-file .env.docker ps

# 应用日志
sudo docker compose --env-file .env.docker logs -f backend

# 重启 staging
sudo docker compose --env-file .env.docker up -d

# 停止 staging，保留数据库卷
sudo docker compose --env-file .env.docker down

# 停止并删除 staging 数据卷（会清空 staging 数据库）
sudo docker compose --env-file .env.docker down -v
```

### 4.1 集成测试库

云服务器创建独立 schema：

```text
btry_it
```

Redis 使用 DB 15 隔离。集成测试通过 Maven 容器执行：

```bash
bash /tmp/btry-run-it.sh
```

该脚本从 `.env.docker` 读取数据库/Redis 密码，并注入假 COS 凭据；不需要 Docker socket，使用 `--network host` 访问回环端口。

### 4.2 观测栈

```bash
sudo docker compose --env-file .env.docker \
  -f compose.yml -f compose.observability.yml \
  --profile observability up -d prometheus grafana jaeger

sudo docker compose --env-file .env.docker \
  -f compose.yml -f compose.observability.yml \
  --profile observability stop prometheus grafana jaeger
```

Grafana 密码来自 `.env.docker` 的 `GRAFANA_ADMIN_PASSWORD`。观测栈默认不启动，测试结束应停止以释放内存。

### 4.3 压测

```bash
# 导入隔离 LOAD 系列
sudo docker compose --env-file .env.docker exec -T mysql \
  mysql -uroot btry_staging < tools/k6/seed-load-series.sql

# smoke / full load
sudo docker run --rm -u 0 --network host \
  -v /home/ubuntu/btry-staging/tools/k6:/scripts \
  -e BASE_URL=http://127.0.0.1:18080 \
  -e STUDENT_PASSWORD="$STUDENT_PASSWORD" \
  grafana/k6:0.54.0 run /scripts/load.js

# 清理
sudo docker compose --env-file .env.docker exec -T mysql \
  mysql -uroot btry_staging < tools/k6/cleanup-load-data.sql
```

## 5. 本次验证记录

验证日期：2026-11-05。

镜像与容器：

- `btry-web:latest` 构建成功。
- `btry-backend:latest` 构建成功；使用阿里云 Maven 镜像后后端镜像构建约 30～45 秒。
- MySQL、Redis、backend、web 四个容器均达到 healthy。
- 后端使用默认 profile，Flyway 已应用 V1～V13；V13 为活跃订单复合索引。

接口与链路：

- `GET /api/health` 返回 `{"status":"ok"}`。
- `GET /actuator/health/readiness` 返回 `UP`，数据库组件为 `UP`。
- 学生账号登录返回 `200`，首次登录 `mustChangePassword=true`。
- 修改初始密码返回 `200`，重新登录后 `mustChangePassword=false`。
- 学习空间返回 2 个系列：`物理必修一`、`物理必修二`。
- `物理必修一` 返回 2 个章节、6 个视频。
- 获取视频播放地址返回 `200`，URL 指向 `rag-1325750042.cos.ap-shanghai.myqcloud.com`。
- 对播放 URL 发起 `Range: bytes=0-1023`，COS 返回 `206`、`video/mp4`、`Content-Range: bytes 0-1023/14611984`。

回归测试：

- 后端单元测试：39 个测试类、124 个测试，0 失败。
- 集成测试：11 个场景，连续 5 轮共 55 次执行，0 失败。
- 前端在 Docker 内执行 `npm run build` 成功。

并发与恢复：

- 并发下单、并发支付、重复回调、金额不一致、成功后乱序失败、并发退款、退款与履约竞态均通过。
- 真实进程重启：订单 `ORD202611051810008CBAB7B7` 从 PAID 自动补履约到 FULFILLED，学习空间出现课程。

SQL：

- 独立 `btry_perf` schema：10,000 订单、25,000 支付事件、1,000 退款、10,000 播放日志。
- 活跃订单查询从主键反向扫 10,000 行（约 10ms）降到复合索引范围扫描（约 0.027ms）。
- 其他核心查询均已命中现有索引，未盲目增加索引。

JVM/GC：

- G1、最大堆约 360MB、容器上限 512MiB。
- 压测后 YGC 281、FGC 0、CGC 74，累计 GC 1.322s。
- 最大 GC 暂停约 10ms，压测后堆 93MB / 140MB。
- Hikari 峰值 active=3、pending=0。

压测：

- 130 最大 VU（100 browse + 20 order read + 5 playback + 5 order write）。
- 75,271 请求，0 失败，100% checks，约 312.55 req/s。
- 整体 p95 6.36ms，最大 87.12ms。
- 订单写 p95 54.25ms，播放控制面 p95 9.35ms。

链路追踪：

- Jaeger 已收到 `btry-learning-space` 服务。
- 支付确认 trace：HTTP → `payment.confirm` → `order.fulfill`。
- 退款 trace：HTTP → `payment.refund`。
- 播放 trace：HTTP → `playback.url.resolve`，包含 `cache.hit` 和 `video.key`。
- 响应头 `X-Trace-Id`、日志 MDC、Jaeger traceId、span 标签 `btry.trace_id` 一致。

浏览器验收（通过 SSH 隧道访问 staging）：

- 学生账号登录成功，进入学习空间，看到 2 个系列。
- 进入 `物理必修一`，视频播放器加载 COS 地址并正常播放。
- 从第 1 个视频切换到第 3 个视频后，`currentTime` 从 0 开始增长。

## 6. 运维与安全注意

- 后端、Actuator、MySQL、Redis、Prometheus、Grafana、Jaeger 默认只绑定 `127.0.0.1`。
- `TRUST_FORWARDED_HEADERS=true` 只在后端位于可信 nginx 之后时使用；直接暴露后端时应改为 `false`。
- `.env.docker` 含数据库、Redis、JWT、COS 和 Grafana 凭据，必须 `chmod 600`，且已被 `.gitignore` 忽略。
- Docker 日志使用 json-file 轮转，单文件 20MB、保留 3 个；应用日志和 GC 日志写入 `backend-logs` 卷的 `/app/logs`。
- JDK 运行时比 JRE 大约多 100MB，但保留 JVM 诊断能力；生产 systemd 不受影响。
- MySQL/Redis 数据使用命名卷。`docker compose down` 保留数据，`down -v` 会删除数据。
- staging 数据可丢弃；不要把 staging 数据库当成生产备份。
- 观测栈与 staging 同时运行会增加 swap，压测/排查结束应停止观测栈。
- Docker 构建时 npm 报告 4 个前端依赖漏洞（3 moderate、1 high），本次未处理，上线前应单独评估和升级。
- 生产备份脚本 `deploy/btry-backup.sh` 只覆盖生产数据库，不覆盖 staging 卷。

## 7. 故障排查

- MySQL 无法启动：检查 `.env.docker` 中的 root 密码、数据卷和宿主机端口占用。
- Redis 认证失败：确认 `REDIS_PASSWORD` 同时传给 Redis 和后端。
- 后端启动失败：先看 `docker compose logs backend`，再检查 Flyway、`DB_URL`、`JWT_SECRET`。
- 前端 502：确认 backend 健康，且 nginx 中的 `backend:8084` 与服务名一致。
- 端口冲突：确认 systemd 仍占用 `8084/9091` 时，staging 使用 `18084/19091`。
- Integration 测试连接失败：确认 `btry_it` schema、`btry` 用户授权和 `13306/16379` 回环端口。
- k6 `permission denied`：使用 `-u 0` 或调整脚本目录权限。
- Prometheus p95/p99 为空：确认 `management.metrics.distribution.percentiles-histogram.http.server.requests=true`，并等待至少一个 scrape 周期。
- 业务 span 没进 Jaeger：先确认 `TRACING_ENABLED=true`、endpoint 指向 `jaeger:4318/v1/traces`；短生命周期测试可能来不及 flush，优先在长期运行的 staging 验证。
- Maven 构建长时间无输出：确认 `server/docker/maven-settings.xml` 已复制进镜像；Dockerfile 已不再使用 `dependency:go-offline`。
- Actuator 从宿主机访问不到：默认只绑定 `127.0.0.1`，这是预期行为。

## 8. 结论

当前 Docker staging 已覆盖构建、健康检查、集成测试、SQL 审计、JVM/GC、观测面板、链路追踪和 k6 压测，生产 systemd 未受影响。是否把生产迁移到 Compose 仍不建议仓促决定；现有 systemd + nginx 更简单，Docker 的主要价值是 staging、可重复验证和故障演练。
