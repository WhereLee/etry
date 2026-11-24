# 真实故障演练：后端 down → 告警 → 恢复（2026-11-23）

## 时间线（CST）
- 23:54:23 演练开始：`/learn/` 200、`/api/health` 200、readiness UP
- 23:54:24 `docker stop btry-backend-1`（保持约 85s，模拟后端不可用）
- 23:55:49 演练中：`/learn/` **200**（静态站仍可用）、`/api/health` **502**
- 23:55:49 Prometheus 查询：`BtryBackendDown` **firing**（`ALERTS{alertname="BtryBackendDown"} = 1`）
- 23:55:49 alert-sink 收到 firing webhook（`alertname=BtryBackendDown`）
- 23:55:50 `docker start btry-backend-1`
- 23:56:05 readiness **UP**（重启到就绪 **15s**）
- 23:56:05 `/learn/` 200、`/api/health` 200
- 23:56:45 左右 alert-sink 收到 **resolved** webhook（`send_resolved: true`）

## 结论
- PASS：后端故障被 Prometheus 规则捕获，Alertmanager 成功投递 firing 与 resolved。
- 静态前端不受后端宕机影响；API 在演练期间 502，重启 15s 恢复。
- 本次 API 不可用时长约 101s（含刻意保持的 85s），不包含真实故障的自动恢复（容器未设置自动重启，手动 `start`）。
- 演练后资源：backend 413MiB/640MiB、MySQL 354MiB/512MiB、宿主可用约 2.0G。

## 证据
- Prometheus：`/api/v1/query?query=ALERTS{alertname="BtryBackendDown"}` → firing
- alert-sink：`docker logs btry-alert-sink-1` 含 `BtryBackendDown` 的 firing/resolved JSON
- 演练为主动停容器，属于受控故障注入；未改动数据库与业务数据。
