# P1 交易内核加固（2026-11-24）

## 1. `pay()` 事务边界
- 问题：`@Transactional` 的 pay() 在事务内调用支付渠道（网络），长时间持有订单行锁。
- 修复：`PaymentAttemptStarter` 只负责「置 PAYING + 创建 attempt」的 READ_COMMITTED 事务；渠道下单在事务提交后执行。
- 语义：渠道失败时 attempt 保留，订单保持 PAYING，客户端重试复用同一 attempt/outTradeNo；避免旧实现整段回滚。
- 单测 `PaymentServiceTest` 更新；全量集成 101/101。

## 2. Outbox 多实例认领
- 问题：原 `UPDATE ... ORDER BY LIMIT` 在多 relay 并发下互相阻塞，缺少 SKIP LOCKED。
- 修复：`OutboxRepository.claimBatch` 改为事务内 `SELECT id ... ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED` → `UPDATE ... WHERE id IN (...)`；租约与 lease_token 校验不变。
- 新增 `OutboxIntegrationTest.concurrentRelaysClaimDisjointBatches`：双线程各领 1 条，断言 2 条不重复、全部 PROCESSING。
- Outbox 集成 6/6；全量 101/101。

## 3. 试读缓存与 200 VU 容量
- 200 VU 首测 OOM（9.33% 失败、p95 25s、JVM heap OOM、容器重启）。
- 修复：试读 PDF 按「源文件+页数」磁盘缓存 + 4 并发生成许可；backend 1G、MySQL 768M。
- 复测：23830 请求、788.9 RPS、p95 233.82ms、0% 失败；backend 592M/1G、MySQL 500M/768M。
- 详见 `2026-11-24/p1-capacity-preview-cache.md`。

## 4. 验证与部署
- 单测 241/241；全量集成 101/101（含退款并发、支付链路、outbox、RBAC）。
- 新镜像部署；readiness UP；`/learn/` 与 `/api/health` 200；后端启动无 ERROR/Exception。
- 当前资源：backend 360M/1G、MySQL 433M/768M、宿主可用约 1.9G。

## 5. 剩余 P1 项（转入后续）
- 退款渠道调用租约心跳（当前 lease 远大于渠道超时，依赖 provider idempotency；可后续补）。
- 对账差异唯一键与重跑幂等（需存量数据清洗评估）。
- 双后端实例 + nginx 流量分流演练（outbox 双 relay 已由集成测试覆盖；实例级演练待做）。

## 证据
- `/tmp/it-pay-boundary.log`（101/101）
- `/opt/etry/perf/k6-read-200b.log`（200 VU）
- 代码：`PaymentAttemptStarter.java`、`PaymentDigests.java`、`PaymentService.java`、`OutboxRepository.java`、`MaterialFileStore.java`
