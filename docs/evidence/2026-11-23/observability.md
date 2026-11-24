# 可观测性与告警（2026-11-23）

## 观测栈
- Prometheus v2.54.1：`127.0.0.1:19090`（仅回环）
- Grafana 11.2.2：`127.0.0.1:13000`（仅回环；密码在 `/opt/etry/.env.docker` 的 `GRAFANA_ADMIN_PASSWORD`，600）
- Alertmanager v0.27.0：`127.0.0.1:19093`（仅回环）
- alert-sink：容器网络内 `8099`，接收 webhook 并写 Docker 日志
- 后端管理端口 9091：容器内 `0.0.0.0`，**不发布到宿主**；Prometheus 通过 compose 网络抓取 `backend:9091`
- 启动命令：`docker compose -f compose.yml -f compose.server.yml -f compose.observability.yml --profile observability up -d`

## 抓取与业务指标
- 目标：`btry-backend` → `up{job="btry-backend"} = 1`（instance `backend:9091`）
- `PaymentAlertJob` 每 5 分钟巡检时同步 6 个业务 gauge：
  - `btry_payment_paid_unfulfilled`
  - `btry_payment_paying_stuck`
  - `btry_payment_refund_retry_exhausted`
  - `btry_payment_duplicate_unrefunded`
  - `btry_payment_multi_success_manual`
  - `btry_outbox_dead`
- 基础指标：HTTP 5xx、Hikari pending、JVM heap。

## 告警规则（9 条）
- `BtryBackendDown`（up==0 持续 1m，critical）
- `BtryHigh5xx`（5xx 速率 >0.2/s 持续 2m，warning）
- `BtryDbPoolPending`（Hikari pending >0 持续 2m，warning）
- `BtryHighHeap`（JVM heap >85% 持续 5m，warning）
- `BtryPaidUnfulfilled` / `BtryPayingStuck` / `BtryRefundRetryExhausted` / `BtryDuplicateUnrefunded` / `BtryOutboxDead`

## 告警投递
- Alertmanager 路由 `default` → `http://alert-sink:8099/alert`，`send_resolved: true`。
- 合成演练：`amtool alert add Drill` → sink 收到 firing JSON。
- 真实故障演练：停后端约 85s，`BtryBackendDown` firing 与 recovered 后的 resolved 均送达；详见 `failure-drill.md`。
- 应用侧：后端 `ALERT_WEBHOOK_URL=http://alert-sink:8099/alert`；`PaymentAlertJob` 的业务告警写入同一 sink。

## 资源（演练后）
- backend 413MiB/640MiB；MySQL 354MiB/512MiB；Redis 1.7MiB/160MiB
- Prometheus ~21MiB/320MiB（随数据增长）；Grafana ~49MiB/256MiB；Alertmanager ~14MiB/64MiB；alert-sink ~12MiB/32MiB
- 宿主可用约 2.0G

## 未覆盖与下一步
- 业务 gauge 目前随 5 分钟巡检更新；若要求秒级，可改为独立 60s 采样。
- 为 alert-sink/Alertmanager 增加日志轮转；补充 Grafana 面板截图与使用说明。
- P1：按 k6 结论调整 backend/MySQL 内存限额并复测 200 VU。

## 证据位置
- Prometheus：`/api/v1/targets`、`/api/v1/rules`、`/api/v1/query`
- sink 日志：`docker logs btry-alert-sink-1`
- 服务状态：三个 compose 文件 + `--profile observability ps`
