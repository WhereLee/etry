# 订单链路 SQL 与索引审计

更新日期：2026-11-05

## 1. 审计范围与数据

审计在独立 `btry_perf` schema 上执行，不影响 staging 演示库和生产库。

数据规模：

- 订单：10,000
- 支付尝试：约 9,000
- 支付事件：25,000
- 退款：1,000
- 播放日志：10,000
- 学习进度：10,000
- 管理审计：5,000

数据由 `tools/test-data/generate_perf_data.py` 生成，查询脚本为 `tools/test-data/explain-order-queries.sql`。

## 2. 关键查询结果

| 场景 | 使用索引 | 实际行数/扫描 | 结果 |
|---|---|---:|---|
| 用户订单分页 | `idx_order_user_created` | 20 行，索引反向扫描 | 约 0.10ms |
| 活跃订单查找 | 新增 `idx_order_user_series_status` | 4 行估算，索引范围扫描 | 约 0.027ms |
| 待查单订单 | `idx_order_status_updated` | 50 行 | 约 0.24ms |
| 过期订单 | `idx_order_status_expire` | 0 行，排序输入很小 | 约 0.02ms |
| 已支付未履约 | `idx_order_status_expire` 的 `status='PAID'` 前缀 | 扫描 1,000 行后排序取 50 | 约 2.4ms |
| 最新支付尝试 | `uk_payment_attempt_no` | 1 行 | 约 0.017ms |
| 待处理退款 | `idx_refund_status` | 0 行 | 约 0.014ms |
| 可重试退款 | `idx_refund_retry` | 候选 99 行，取 50 | 约 0.46ms |
| 支付事件幂等查询 | `uk_payment_event_key` | 1 行 | 索引唯一查找 |
| 播放日志按用户 | `idx_playback_user_time` | 50 行 | 约 0.23ms |
| 播放日志按视频 | `idx_playback_video_time` | 50 行 | 约 0.21ms |
| 管理审计按操作人 | `idx_admin_audit_operator_time` | 50 行 | 约 0.17ms |

## 3. 本次索引变更

新增 Flyway `V13__order_active_lookup_index.sql`：

```sql
ALTER TABLE t_order
    ADD KEY idx_order_user_series_status (user_id, series_id, status, id);
```

原因：

- `OrderRepository.findActive` 使用 `user_id + series_id + status IN (...) ORDER BY id DESC LIMIT 1`；
- 变更前 `EXPLAIN ANALYZE` 显示 MySQL 反向扫描主键并过滤 10,000 行，耗时约 10ms；
- 变更后使用复合索引范围扫描，耗时约 0.027ms；
- 该查询位于下单和发起支付前的关键路径，属于明确的索引缺口。

没有新增其他索引：

- 已支付未履约当前只扫描约 1,000 个 PAID 订单并排序，2.4ms；补偿任务按批 50 处理，暂不构成瓶颈；
- 过期订单、退款重试、播放日志、审计日志均已命中现有索引；
- 避免为了“索引数量”增加写放大。

## 4. 连接池观察

- 生产默认 Hikari 最大连接数 10，最小空闲 2，连接超时 3 秒。
- 集成测试使用 24 个连接，是为了让 20 个并发线程真实排队，不代表生产建议值。
- 当前 SQL 单次执行在毫秒级，10 个连接对 10～20 人机构足够；是否调整要由 k6 压测中的 `hikaricp_connections_pending` 和获取时间决定。

## 5. 复现方式

```bash
# 1. 生成 SQL
python3 tools/test-data/generate_perf_data.py --orders 10000 --out /tmp/btry-perf-data.sql

# 2. 在一次性 btry_perf schema 上执行全部 migration
# 3. 导入数据
# 4. 执行 explain-order-queries.sql
```

不要在生产库执行生成脚本；它包含 `TRUNCATE`。

## 6. 结论

- 核心订单、支付、退款、播放和审计查询都有可用索引；
- 唯一明确缺口是活跃订单复合索引，已通过 V13 修复；
- 其余查询暂不调索引，等待压测数据决定；
- SQL 层面没有发现需要立刻重构的全表扫描热点。
