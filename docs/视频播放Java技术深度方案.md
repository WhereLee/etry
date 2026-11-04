# 视频播放 Java 技术深度方案

> 整理日期：2026-11-02  
> 范围：btry 视频播放、URL 签发、缓存、日志、监控  
> 说明：这是“技术深度版”设计，不代表所有内容都必须一次性实现。

---

## 一、判断标准

技术深度不等于框架数量。

真正有意义的深度应体现在：

- 并发一致性
- 缓存击穿和热点处理
- 多实例协调
- 故障隔离和降级
- 幂等和重复请求处理
- 异步日志背压
- 可观测性
- 失败注入和并发测试

如果只是把 `ConcurrentHashMap` 换成 Redis、把 `JdbcTemplate` 换成 MyBatis、随手加几个注解，但没解决上述问题，那只是技术栈堆叠。

---

## 二、目标架构

```text
PlaybackUrlService
  ├─ 进程内缓存
  ├─ Redis 分布式缓存
  ├─ Redis 单飞锁
  ├─ TTL 抖动
  └─ COS 客户端（超时/重试/熔断）

PlaybackEventService
  ├─ 有界队列
  ├─ 批量写库
  ├─ 幂等键
  └─ 队列满时的降级策略

PlaybackAuditAspect
  ├─ @PlaybackAudit
  ├─ @RateLimit
  ├─ @Idempotent
  └─ @Measured

PlaybackLogMapper（MyBatis）
  ├─ 动态筛选
  ├─ 批量插入
  ├─ 分页查询
  └─ 日报/小时报统计

PlaybackMetrics
  ├─ URL 请求数
  ├─ 缓存命中率
  ├─ COS 错误率
  ├─ p95/p99 耗时
  └─ 告警阈值
```

---

## 三、AOP 和自定义注解

AOP 只处理横切关注点，不把核心业务藏进切面。

### 1. `@PlaybackAudit`

用于：

- 播放 URL 签发
- URL 刷新
- 播放错误
- 管理动作

记录：

- 用户
- 视频
- 系列
- 是否命中缓存
- 结果
- 耗时
- traceId/sessionId

### 2. `@RateLimit`

用于：

- 按用户限流
- 按视频限流
- 按 IP 限流

建议用 Redis + Lua 实现滑动窗口，保证原子性。

### 3. `@Idempotent`

用于：

- 播放事件上报
- URL 刷新事件
- 重复请求去重

幂等键可以是：

```text
userId + videoId + eventType + sessionId + 时间窗口
```

### 4. `@Measured`

自动记录：

- 方法耗时
- 成功/失败
- 缓存命中
- COS 调用耗时

输出到 Micrometer，而不是只写日志。

### 5. 不建议

- 用 AOP 隐藏核心业务逻辑。
- 每个方法都加注解。
- 只为了“用了 AOP”而加切面。

---

## 四、Redis 的使用

Redis 不只是把 Map 换掉。

### 1. 分布式播放 URL 缓存

- key：`playback:url:{videoKey}`
- value：URL + expiresAt
- TTL：略小于预签名有效期
- TTL 加随机抖动，避免同时失效

### 2. 分布式单飞锁

目的：多个实例同时未命中时，只让一个实例生成 URL。

- `SET key value NX PX ttl`
- 释放锁时用 Lua 校验持有者
- 等待方短暂重试读取缓存
- 超时后降级为各自生成

### 3. 限流

- 滑动窗口或令牌桶
- 用 Lua 保证原子性
- key 可按用户、视频、IP 组合
- 超限返回明确错误，不拖垮数据库或 COS

### 4. 热 key

- 对高频视频单独统计
- 可加本地 L1 缓存
- 避免单个热点把 Redis 或 COS 打满

### 5. 失败策略

- Redis 不可用时，URL 缓存降级为本地缓存或直接生成
- 限流可以降级为本地计数
- 不能因为 Redis 故障导致所有播放不可用

---

## 五、MyBatis 的使用

MyBatis 应该在查询复杂、动态条件多的地方使用，而不是简单 CRUD。

适合：

- 播放日志动态筛选
- 分页查询
- 批量插入
- 日报/小时报聚合
- 按用户、视频、事件类型统计
- 复杂关联查询

不适合：

- 只按主键查一条
- 只有一个固定 SQL
- 为了“看起来用了 MyBatis”而替换简单 JdbcTemplate

建议保留：

- `JdbcTemplate` 用于简单写入和运维 SQL
- MyBatis 用于日志查询和统计

---

## 六、异步日志与背压

当前是同步写库。技术深度版应改为：

- 有界队列
- 批量插入
- 独立线程池
- 队列满时的策略：
  - 丢弃低价值事件
  - 阻塞请求
  - 写本地降级文件

推荐：

- `ERROR`、`URL_REFRESH` 高优先级
- `SEEKING`、`PLAY` 普通优先级
- `WAITING`、`STALLED` 可采样
- 不允许日志写失败影响播放

---

## 七、COS 故障隔离

需要：

- 连接超时
- 读取超时
- 有上限的重试
- 熔断
- 线程池隔离
- 错误分类
- 预签名失败时的降级

建议使用 Resilience4j，而不是手写所有重试和熔断。

---

## 八、可观测性

技术栈：

- Spring Boot Actuator
- Micrometer
- Prometheus 可选
- Grafana 可选

指标：

- URL 请求数
- 缓存命中率
- 单飞锁等待时间
- COS 调用耗时
- COS 错误率
- 播放错误数
- STALLED/WAITING 比率
- p95/p99 耗时

日志：

- traceId
- userId
- videoId
- sessionId
- cacheHit

---

## 九、本地环境检查结果

检查日期：2026-11-02

| 技术/中间件 | 本地状态 | 说明 |
|---|---|---|
| Java | 已安装 | 17.0.10 |
| Maven | 已安装 | 3.9.9 |
| MySQL | 已运行 | 8.0.44，3306 可连接 |
| Redis | 已手动启动运行 | 5.0.14.1，`F:\Redis`，6379 可连接；Windows 服务本身启动异常 |
| Docker | 未安装 | `docker` 命令不存在 |
| Docker Compose | 未安装 | 无 |
| Prometheus | 未安装 | 无命令 |
| Grafana | 未安装 | 无命令 |
| WSL | 未安装 | `wsl -l -v` 不可用 |
| RocketMQ | 已安装，未启动 | 5.3.1，`D:\rocketmq-5.3.1`，9876/10911/10909 当前未开放 |

### 项目依赖现状

当前 `server/pom.xml` 已加入：

- `spring-boot-starter-aop`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-actuator`
- `mybatis-spring-boot-starter`
- `micrometer-registry-prometheus`

当前仍未加入：

- `resilience4j`
- `testcontainers`

---

## 十、建议实施顺序

### 阶段 1：缓存与并发

- Redis URL 缓存
- 分布式单飞锁
- TTL 抖动
- 本地降级
- 并发测试

### 阶段 2：AOP 与限流

- `@PlaybackAudit`
- `@RateLimit`
- `@Measured`
- Redis + Lua 限流

### 阶段 3：日志与 MyBatis

- 有界队列
- 批量插入
- MyBatis 动态查询
- 日报统计

### 阶段 4：稳定性

- Resilience4j 超时/重试/熔断
- Micrometer 指标
- 失败注入测试
- 告警阈值

---

## 十一、可选 MQ 方案：RocketMQ

本地已有 RocketMQ 5.3.1，但当前未启动。它不是视频播放的必需组件。

### 默认方案

播放事件继续使用：

- 有界队列
- 批量写库
- 失败降级
- 不依赖 MQ

### 可选方案

如果后续需要跨实例持久化、削峰、重放和消费端解耦，可以改为：

```text
PlaybackEventService
  -> RocketMQ Producer
  -> Topic: playback-event
  -> Consumer
  -> MyBatis batch insert
  -> t_playback_log
```

### 使用 MQ 后必须补的内容

- Topic 和消费组命名。
- 消息 key：`userId + videoId + sessionId + eventType`。
- 消费端幂等。
- 失败重试和死信队列。
- 顺序性要求：同一 session 是否必须有序。
- 消费位点和监控。
- 生产者发送失败降级。
- 本地开发启动脚本和端口约定。

### 适用条件

- 需要多实例共享事件流。
- 需要事件重放或离线补偿。
- 需要把日志写入与请求线程彻底解耦。
- 愿意承担 MQ 的启动、监控和运维成本。

### 当前建议

视频字节仍然直接走 COS。  
MQ 只用于事件，不用于视频传输。  
当前默认不启用 RocketMQ；先完成 Redis、AOP、异步队列和 MyBatis 的基础深度，再决定是否把队列替换为 RocketMQ。

---
## 十二、明确不做

- 不拆微服务。
- 不默认引入 MQ；已有 RocketMQ 作为可选异步事件通道，不用于视频字节。
- 不为了 AOP 而把业务逻辑全部切面化。
- 不为了 Redis 而缓存所有查询。
- 不为了 MyBatis 而重写所有简单 SQL。
- 不为了“技术深度”引入无实际用途的中间件。
---

## 十三、A+C 执行结果

### 已完成

1. **Actuator + Micrometer**
   - 管理端口：`127.0.0.1:9091`
   - `/actuator/health` 可用
   - `/actuator/prometheus` 可用
   - health 显示 MySQL、Redis、磁盘均 UP
   - 自定义指标：
     - `playback.url.cache`（local_hit / redis_hit / miss / lock_wait / lock_timeout / redis_error）
     - `playback.url.cache.local.size`
     - `playback.url.presign`
     - `btry.method`（`playback.play`、`playback.urlCache.get`、`playback.log.record`）
     - `playback.log.queue.size`
     - `playback.log.persisted`
     - `playback.log.persist.errors`
     - `playback.log.dropped`

2. **Redis URL 缓存**
   - L1：进程内 `ConcurrentHashMap`
   - L2：Redis `btry:playback:url:{videoKey}`
   - 分布式单飞锁：`SET NX PX` + Lua 释放
   - TTL 抖动
   - Redis 故障降级到本地生成
   - 实测同一视频两次播放返回同一 URL，Redis 命中指标增加
   - Redis key 已实际写入

3. **AOP + 自定义注解**
   - `@Measured`：记录方法耗时
   - `@RateLimit`：Redis + Lua 固定窗口限流
   - 播放接口限流实测：超过阈值返回 HTTP 429
   - 限流组件故障时放行，避免 Redis 故障导致播放不可用

4. **异步日志 + MyBatis**
   - 有界队列 `ArrayBlockingQueue(5000)`
   - 批量写入 `t_playback_log`
   - 队列满时丢弃并计数
   - 后台写库线程
   - MyBatis `insertBatch`
   - MyBatis 动态查询：`GET /api/playback/logs?eventType=&limit=`
   - 实测异步写入和查询正常

5. **COS 客户端稳定性**
   - 连接超时：3 秒
   - 读超时：15 秒
   - 最大重试：2 次
   - 可通过 `COS_CONNECT_TIMEOUT_MS`、`COS_READ_TIMEOUT_MS`、`COS_MAX_RETRIES` 覆盖

### 仍未完成

- 主动告警的接收方 webhook 尚未配置（告警已能入库，未配置 webhook 时记录错误）。
- Prometheus/Grafana 本地安装。
- RocketMQ 未启动，未接入。
- 真实 10～40 分钟视频验证和真实 500 人播放测试仍等待真实条件。
- 更完整的失败注入场景（MySQL 慢、队列满、COS 签名配置错误）尚未形成独立测试集。

### 当前说明

- Redis 使用的是当前用户启动的临时进程，不是 Windows 服务。
- 如果机器重启，需要重新启动 Redis。
- 启动命令：
  ```powershell
  Start-Process -FilePath 'F:\Redis\redis-server.exe' `
    -ArgumentList 'F:\Redis\redis.windows.conf' `
    -WorkingDirectory 'F:\Redis' -WindowStyle Hidden
  ```
---

## 十四、收窄版实施计划（已确认）

### 1. Resilience4j

**范围**

- 保护 Redis 读取、写入、单飞锁和限流。
- 保护异步日志批量写库。
- 不保护 COS 预签名生成。

**原因**

- 预签名 URL 是本地签名计算，不经过视频传输网络；给预签名加熔断对视频链路保护有限。
- 真正可能拖慢后端的是 Redis 和 MySQL 日志写入。

**目标**

- Redis 连续失败后快速失败并降级。
- 日志数据库连续失败后不阻塞播放。
- 有半开恢复和指标。

### 2. @PlaybackAudit

**范围**

- 挂在播放 URL 签发路径。
- 记录耗时、成功/失败、异常。
- 不新增第二套重复的 URL_REQUEST 日志。

**做法**

- 复用现有 `t_playback_log` 和 Micrometer。
- 只在慢请求或失败时写应用日志。
- 不把业务逻辑藏进切面。

### 3. @Idempotent

**范围**

- 实现通用注解和 Redis 去重切面。
- 暂不挂到 PLAY/SEEKING/WAITING 等播放遥测。
- 暂不改变现有播放行为。

**原因**

- 播放遥测不是命令，去重可能掩盖真实重复行为。
- 幂等更适合未来的支付、下单、凭证刷新等状态变更操作。

### 4. 失败注入测试

**范围**

- Redis 不可用时，URL 缓存回退到本地生成。
- Redis 超时时，限流和缓存不阻塞播放。
- MySQL 日志写入失败时，播放不受影响。
- 日志队列满时，丢弃计数增加。
- COS 预签名配置错误时，错误分类明确。

**工具**

- Mockito + JUnit。
- 本地 Redis/MySQL 可选。
- 不要求 Docker/Testcontainers。

### 5. 实施顺序

1. Resilience4j 包 Redis 和日志写库。
2. `@PlaybackAudit`。
3. 通用 `@Idempotent`。
4. 失败注入测试。
5. 更新 README 和本方案文档。

### 6. 明确不做

- 不给 COS 预签名伪装熔断。
- 不把 `@Idempotent` 套到播放事件上报。
- 不为了失败注入引入 Docker。
- 不把技术深度做成框架堆叠。
---

## 十五、收窄版执行结果

### 已完成

1. **Resilience4j**
   - 新增 `resilience4j-spring-boot3` 和 `resilience4j-micrometer`。
   - 熔断器 `redis`：保护 Redis 读取、写入、单飞锁和限流。
   - 熔断器 `playbackLogDb`：保护异步日志批量写库。
   - `playbackLogDb` 配置重试。
   - `/actuator/prometheus` 已出现 `resilience4j_circuitbreaker_*` 指标。
   - 实测 Redis 熔断器成功调用计数增加，日志写库熔断器成功调用计数增加。

2. **@PlaybackAudit**
   - 已实现注解和切面。
   - 挂在 `SeriesService.play`。
   - 记录 `playback.audit` 耗时和 `playback.audit.failures`。
   - 慢请求/失败才写 WARN，普通请求写 DEBUG。
   - 实测 `playback_audit_seconds_count` 增加，未产生重复的 `t_playback_log` 记录。

3. **@Idempotent**
   - 已实现通用注解和 Redis 去重切面。
   - 使用 Redis `SET NX PX`。
   - Redis 不可用时放行。
   - **没有挂到播放遥测**，避免掩盖真实重复事件。
   - 当前没有实际使用点，留给未来的状态变更操作。

4. **失败注入测试**
   - `PlaybackUrlCacheTest`：Redis 不可用时，仍然生成本地 URL。
   - `PlaybackLogQueueTest`：队列满时，`playback.log.dropped` 增加。
   - `mvn test` 通过。

### 当前仍不做

- 不给 COS 预签名加熔断。
- 不把幂等切面套到 PLAY/SEEKING/WAITING 等遥测。
- 不引入 Docker/Testcontainers。
- 不做主动告警推送。
- 不启动 RocketMQ。

### 验证结果

- Redis、MySQL、Actuator health 均为 UP。
- Redis URL 命中指标增加。
- `btry.method` 包含 `playback.play`、`playback.urlCache.get`、`playback.log.record`。
- `playback.audit` 指标增加。
- `playback.log.persisted` 增加。
- `resilience4j_circuitbreaker_calls_seconds_count` 中 `redis` 和 `playbackLogDb` 成功调用均增加。
---

## 十六、主动告警执行结果

### 已完成

- 新增 `t_alert_log` 告警记录表。
- 新增 `PlaybackAlertJob` 定时评估器。
- 评估窗口默认 10 分钟，评估间隔默认 5 分钟。
- 阈值：
  - 播放错误 ≥ 5
  - 卡顿 ≥ 10
  - URL 缓存未命中率 ≥ 80% 且请求 ≥ 20
  - 日志队列出现丢弃
  - Redis 或日志写库熔断器不是 CLOSED
- Redis 去重：同一类型告警在 30 分钟内只发送一次。
- Webhook 未配置时仍写入 `t_alert_log`，`delivery_error` 记录 `webhook not configured`。
- 已实测：
  - 5 条 `ERROR` 产生 1 条 `PLAYBACK_ERRORS` 告警。
  - 再插入 5 条错误后，没有重复告警。
  - Redis 去重 key TTL 正常。
- Webhook 使用通用 JSON：
  ```json
  { "text": "告警内容" }
  ```
  如果接飞书/企业微信，需要按对应格式调整 `AlertNotifier`。

### 仍缺

- 真实 webhook 地址。
- 告警接收方的消息格式适配。
- 告警恢复通知。
---

## 十七、Video v1 Frozen

**状态：FROZEN（2026-11-02）**

### 已交付

- 学生登录、学习空间、系列与章节播放页。
- COS 直连播放，不使用 CDN，不经过云服务器。
- faststart 视频对象。
- 本地 L1 + Redis L2 播放 URL 缓存。
- Redis 单飞锁、TTL 抖动、故障降级。
- URL 过期前主动刷新和播放位置恢复。
- 同一对象切换课时不重建播放器。
- 播放事件异步入库。
- MyBatis 批量写入和动态查询。
- 日志保留 30 天。
- 播放统计视图。
- Resilience4j Redis / 日志写库熔断。
- `@Measured`、`@RateLimit`、`@PlaybackAudit`。
- `@Idempotent` 通用能力（未挂播放遥测）。
- Actuator + Micrometer 指标端点。
- 主动告警入库与 Redis 去重。
- 故障注入测试：Redis 断开、COS 预签名失败、队列满、日志写库失败。

### 未完成验证（不阻塞其他开发）

- 真实 10～40 分钟视频的拖动、首帧和流量验证。
- 真实 500 人分布式播放测试。
- 浏览器 Network/Performance 层面的缓存命中验证。
- 真实 webhook 推送（当前只入库）。
- Prometheus/Grafana 可视化。
- RocketMQ 接入。

### 重新打开视频部分的条件

只有出现以下情况才重新评估：

1. 真实 40 分钟视频拖动明显卡顿。
2. 实际 COS 流量持续超过文件大小的 1.3 倍。
3. 日志显示持续 `ERROR` / `STALLED` / 熔断。
4. URL 缓存命中率异常下降。
5. 真实浏览器测试发现重复下载。
6. 需要多码率或 HLS 的真实业务需求出现。

### 冻结期间不做

- 不引入 HLS。
- 不引入分段缓存。
- 不引入 CDN。
- 不接入 RocketMQ。
- 不为了技术深度继续加视频中间件。
- 不为了“再优化一点”反复改播放链路。

### 运维备注

- Redis 当前是当前用户启动的临时进程，机器重启后需重新启动。
- Webhook 未配置时告警只写入 `t_alert_log`。
- 后端 8084，前端 5175，Actuator 9091。