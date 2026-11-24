# P2 Redis 降级与恢复演练（2026-11-24）

## 环境

- 生产容器 `btry-backend-1`（RestartCount=0，StartedAt=2026-11-23T17:18:04Z）
- Redis 容器 `btry-redis-1`，健康检查 `redis-cli ping`
- RabbitMQ 已启用，backend readiness 不依赖 Redis

## 步骤与结果

### 1. 停止 Redis

```
docker stop btry-redis-1
```

- `/actuator/health/readiness`：`{"status":"UP"}`（未被摘流）
- `/actuator/health`：HTTP 503（整体暴露 Redis DOWN，便于运维发现）

### 2. 登录限流本地兜底

对 `POST https://whereri.site/api/auth/login` 使用不存在用户连续 9 次错误口令：

```
1 401
2 401
3 401
4 401
5 401
6 401
7 401
8 401
9 429
```

说明第 9 次已由 `LocalRateLimiter` 拦截，而不是 Redis 不可用导致放行或 500。

### 3. 降级指标

从 backend `/actuator/prometheus`：

```
infra_redis_fallback_total{component="login_throttle",operation="redis_error"} 9.0
infra_redis_fallback_total{component="login_throttle",operation="write"} 8.0
```

与 9 次检查、8 次失败写入完全对应。

### 4. 恢复 Redis

```
docker start btry-redis-1
```

- Redis 约 9 秒转 healthy。
- readiness 仍为 `UP`。
- backend `RestartCount=0`，应用未重启，Lettuce/熔断器自动恢复。
- 外部 `/api/health` 返回 200。

## 结论

- Redis 故障不会使交易链路失去正确性，也不会摘除 readiness。
- 限流、幂等、播放 URL 缓存均有本地兜底，并通过统一指标暴露。
- Redis 恢复无需重启应用，满足单机生产环境的可运维性要求。
