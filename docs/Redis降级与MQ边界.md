# Redis 降级与 MQ 边界

更新日期：2026-11-03

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

## 6. 为什么暂不引入 MQ

当前系统为单实例，支付履约已有：

- MySQL 事务；
- 支付事件表；
- 主动查单；
- PAID 未履约补偿任务；
- 管理员补履约。

MQ 现在不是必需组件。未来如果引入 RocketMQ，合理落点只有支付履约：

1. 支付确认事务同时写入 `outbox_message`。
2. 发布器将 Outbox 消息投递到 RocketMQ。
3. 消费者幂等发放权益并将订单推进到 `FULFILLED`。
4. 原有定时补偿任务保留，作为最终兜底。

不直接使用“支付回调里发 MQ”，避免数据库与消息双写不一致。

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
