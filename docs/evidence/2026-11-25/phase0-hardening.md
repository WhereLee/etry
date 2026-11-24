# Phase 0 支付一致性硬化（2026-11-25）

## 范围

本轮只增强恢复、幂等和可观测性，不改变价格、退款规则、订单状态含义或用户可见业务流程。

## 1. Outbox 重放

- 新增 `POST /api/admin/outbox/{id}/replay`，仅 `SUPER_ADMIN` 可调用。
- 具备限流、幂等、耗时指标和 `OUTBOX_REPLAY` 管理审计。
- 只允许 `DONE/DEAD` 消息重放；通过租约切换为 `PROCESSING`，投递成功后 `DONE`，失败 `DEAD`。
- Rabbit publisher confirm 成功后仍沿用“已投递”语义；业务消费失败可由管理员再次重放。
- 新增集成测试：死信消息重放后权益不重复、消息恢复 `DONE`。

## 2. 退款权益一致性补偿

- 新增 `EntitlementConsistencyJob`，默认每 60 秒巡检一次。
- 订单模块查询 `REFUNDED` 候选，权益模块通过 `EntitlementQuery` 批量查询 ACTIVE 订单来源权益，保持表归属边界。
- 发现“已退款但权益仍有效”时，调用幂等 `revoke` 自动补偿；失败按订单去重告警并下轮重试。
- 新增指标 `btry_payment_refunded_active_entitlement`，生产当前为 0。
- 新增集成测试：模拟 REVOKE outbox 死信后，巡检仍能将权益改为 `REVOKED`。

## 3. 对账原子性与幂等

- 账单创建、解析、流水写入、`PARSED` 更新放在同一事务；解析失败回滚文件记录，不再遗留 `PARSING`。
- 对账运行在单事务内锁定账单文件行，串行创建 run、匹配、写差异、完成 run；进程崩溃整体回滚，避免 `RUNNING` 半成品。
- `V25__reconciliation_break_identity.sql` 为 NULL 业务标识增加生成列，并建立轮次内唯一键。
- 差异插入使用 `ON DUPLICATE KEY UPDATE` 只更新金额/严重度/详情，不覆盖人工处理状态。
- 新增 `ReconciliationRecoveryJob`，将历史超过 30 分钟的 `PARSING/RUNNING` 任务标记失败，允许重新执行。
- 新增集成测试：差异重复插入幂等、同文件并发 run 串行且版本唯一、非法账单回滚、遗留任务恢复。

## 4. 证据采集

- 新增 `tools/payment-evidence.sh`，只读采集：
  - 订单、支付尝试、支付事件、退款、权益、outbox；
  - 管理审计、告警；
  - 账单文件、run、match、break、action；
  - RabbitMQ 队列、readiness、运行镜像与关键依赖版本；
  - 支付/outbox/对账相关 Prometheus 指标。
- 不输出 token、密钥或买家 PII。
- 部署前/后证据：
  - `docs/evidence/2026-11-25/phase0-before.txt`
  - `docs/evidence/2026-11-25/phase0-after.txt`

## 5. 验证与部署

- 单元 + 集成报告合计：353 个测试，0 Failures，0 Errors（246 单测、107 集成）。
- 部署前检查生产 `t_recon_break` 重复业务身份为 0。
- 备份：`/opt/etry/backups/20261125-024209/`（含 `btry_edu.sql.gz` 与资料归档）。
- `deploy-server.sh` 部署成功；V25 唯一键生效。
- 外部 `/learn/`、`/api/health` 均 200，readiness UP。
- RabbitMQ `fulfill/revoke/dlq` 各 1 个消费者、0 积压。
- 新指标 `btry_payment_refunded_active_entitlement=0`。

## 6. 边界

- 本轮未发起真实支付宝支付或退款；真实渠道验收仍按 `p0-alipay-acceptance-runbook.md` 执行。
- 对账差异处理仍只记录人工结论，不自动改资金事实；这是明确的业务边界。
