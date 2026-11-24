# Redis 降级与 MQ 边界

更新日期：2026-11-24

## 1. 原则

Redis 是重要的性能与协调组件，但不能成为业务正确性的唯一来源。

- 数据库是订单、支付、权益和审计的事实来源。
- Redis 故障时必须按组件选择“本地兜底、降级放行或拒绝请求”。
- 降级不能只写日志，必须暴露指标并进入故障处置手册。
- MQ 暂不引入；未来只在支付履约等明确需要异步解耦的地方使用。

## 2. Redis 使用点与降级策略

| 组件 | 正常行为 | Redis 故障行为 | 风险 |
|---|---|---|---|
| 播放 URL 缓存 | L1 本地 + L2 Redis + 熔断 | 使用本地缓存或重新生成 URL | COS 预签名次数增加 |
| 登录失败限流 | Redis 原子计数 | 本机固定窗口计数 | 多实例时阈值按实例计算 |
| 公开接口限流 | Redis 原子计数 | 本机固定窗口计数 | 多实例时保护能力下降 |
| 通用 `@RateLimit` | Redis 原子计数 | 本机固定窗口计数，超限仍返回 429 | 多实例时阈值按实例计算 |
| `@Idempotent` | Redis SET NX | 本地 Caffeine TTL 幂等 | 多实例时需要数据库唯一键兜底 |
| 支付下单/回调 | MySQL 唯一键和状态机 | 不依赖 Redis 保证资金正确性 | Redis 仅影响限流和辅助幂等 |
| 告警去重 | Redis SET NX | 本地 TTL 去重 | 极端情况下可能重复告警 |

实现：

- `LocalRateLimiter`：Caffeine 固定窗口本地限流。
- `LocalFallbackCache`：Caffeine TTL 本地幂等与告警去重。
- `RedisFallbackMetrics`：统一指标 `infra.redis.fallback`，按 `component` 和 `operation` 标记。
- `PlaybackUrlCache`：保留原有本地 L1 和 Resilience4j 熔断，同时写入统一降级指标。

## 3. 降级边界

### 可以降级放行

- 播放 URL 获取：本地生成，成本上升但用户可继续播放。
- 公开邀请校验、播放等可重试请求。

### 必须保留保护

- 登录失败、支付下单、支付发起、退款等写接口。
- Redis 故障时使用本地限流；本地限流也异常时拒绝请求，不无限放行。
- 订单和权益的唯一性由 MySQL 唯一约束保证，不能依赖 Redis 幂等。
- 金额不一致、重复通知和履约失败必须留下支付事件和告警。

## 4. Readiness

DB 是 readiness 必需依赖；Redis 是可降级依赖。

`application.yml` 配置：

- readiness：`db,ping,readinessState`
- liveness：`livenessState,ping`

整体 `/actuator/health` 仍会显示 Redis 组件状态，便于运维发现降级；readiness 不会因为 Redis 短暂不可用而直接把实例摘除。

## 5. 指标

- `infra.redis.fallback{component, operation}`
- `infra.redis.local.rate_limit.size`
- `infra.redis.local.fallback_cache.size`
- `playback.url.cache{result=redis_error}`
- `security.login.blocked`
- `security.public.request.blocked`
- `payment.*`

Redis 恢复后不需要重启应用；熔断器会进入半开并逐步恢复 Redis 调用。

## 6. MQ 落点与选型（2026-11-24 更新）

支付履约已引入 RabbitMQ，仍然只走事务性发件箱，不在支付回调里直接发消息：

1. 支付确认/退款事务同时写入 `t_outbox_message`，资金变更与消息意图同事务。
2. `OutboxRelay` 用 `FOR UPDATE SKIP LOCKED` + 租约认领消息。
3. `RabbitOutboxTransport` 发布到 `btry.outbox`，等待 publisher confirm 后标记 DONE。
4. `OutboxRabbitConsumer` 幂等履约/撤销；失败重试 3 次后进 DLQ 并写告警。
5. 原有 PAID 未履约补偿任务保留，作为 MQ 之外的最终兜底。

MQ 只承载履约/撤销意图，不用于播放日志、上传完成、审计写操作或订单超时；
这些场景当前同步写或定时扫描足够，引入 MQ 只会增加一致性与运维成本。

### 为什么是 RabbitMQ 而不是 RocketMQ

| 维度 | RabbitMQ 3.13 | RocketMQ 5.x |
|---|---|---|
| 单机资源 | 512m 可稳定运行 | NameServer+Broker+Proxy 通常需 1.5g+ |
| 本机诉求 | 低吞吐、可靠投递、死信、重试 | 高吞吐、分区顺序、事务消息 |
| 运维复杂度 | 一个容器，管理台内置 | 多组件、JVM 参数与磁盘规划 |
| 当前瓶颈 | 单实例资料商城，履约消息量很小 | 当前没有高吞吐/顺序消息场景 |

结论：3.7G 单机还要同时承载 MySQL、Redis、后端与监控，RocketMQ 的资源开销
换不来业务收益。`OutboxTransport` 是端口，未来若出现分区顺序或高吞吐诉求，
可新增 RocketMQ 适配器并复用同一发件箱与幂等消费者，无需改资金链路。

**生产决策（2026-11-24 定稿）**：生产唯一 MQ 为 RabbitMQ 3.13；RocketMQ 5.3.1
只用于 staging 的低配顺序/延迟消息演练，演练容器与网络已销毁，不部署到生产。
演练结论见 `docs/evidence/2026-11-24/p2-rocketmq.md`。不因面试演示引入 RocketMQ、
Elasticsearch、Redisson 等当前没有明确业务需求的新组件。

暂不将 MQ 用于：

- 播放日志；
- 视频上传完成；
- 账号和审计写操作；
- 订单超时。当前 Scheduled 扫描足够。

## 7. 验证

已验证：

- Redis 停止时，整体 health 为 `DOWN`，但 readiness 和 liveness 均为 `UP`。
- Redis 停止时，订单创建、Mock 支付、权益发放和退款仍成功。
- 登录限流在 Redis 停止后切换本地计数，达到阈值仍返回 429。
- 公开请求限流在本地第 21 次请求返回 429。
- 幂等在 Redis 停止后仍能阻止本地重复提交，并产生 `infra.redis.fallback` 指标。
- 播放 URL 缓存继续使用本地兜底。
- 实测降级指标包含 `idempotent`、`login_throttle`、`public_throttle` 和 `rate_limit`。

2026-11-24 生产容器复演（RabbitMQ 已启用）：

- 停止 `btry-redis-1` 后：整体 `/actuator/health` 返回 503，readiness 仍为 `UP`。
- 登录接口连续 8 次错误口令返回 401，第 9 次由本地兜底计数返回 429。
- `infra_redis_fallback_total{component="login_throttle",operation="redis_error"} = 9`，
  `operation="write" = 8`，说明请求均按降级路径执行。
- 重启 Redis 后 readiness 保持 `UP`，backend `RestartCount=0`，无重启自动恢复。
- 同期 RabbitMQ outbox 履约/撤销/死信演练见
  `docs/evidence/2026-11-24/p2-rabbitmq.md`。
