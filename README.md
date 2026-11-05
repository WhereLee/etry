# 学习空间

一个内部录播课程平台：学生登录后浏览课程商店、通过支付宝电脑网站支付（本地开发可用 Mock 收银台）购买系列，并在学习空间按章节观看视频。

所有示例视频都指向 COS 中的同一段真实视频：

`edu/video/91/demo-real-181s-faststart-cache.mp4`（181 秒）

## 功能

- 管理员创建学员账号；教师通过一次性邀请链接激活账号
- 管理员设置课程价格和销售状态，学员在课程商店购买
- 支付宝电脑网站支付与本地 Mock 收银台、订单状态机、支付事件、权益发放和全额退款
- 支付成功但履约失败时由补偿任务自动恢复
- 支付记录、异常订单和管理员人工补偿
- 教师工作台查看、创建和编辑自己的系列
- 系列、章节、视频元数据 CRUD 与排序
- `DRAFT / PUBLISHED / ARCHIVED` 发布状态
- 未就绪视频阻止发布；结构修改自动回到草稿
- 教师浏览器直传 COS，显示上传进度并服务端确认对象
- 学员学习进度、断点续播、已看标记和系列完成度
- 管理员内容治理：查看全部系列、下架、归档和分配情况
- 初始密码首次登录强制修改
- 账号启用/停用、密码重置、旧 token 即时失效
- 登录按用户名 + IP 限流，密码要求 8-32 位且包含字母和数字
- 管理员为学员分配可见系列
- 学生账号登录
- 学习空间展示已分配的系列
- 系列详情展示章节和有序视频列表
- 点击视频从 COS 获取预签名播放地址
- 同一对象切换课时不重建播放器
- URL 过期前主动刷新并恢复播放位置
- 播放日志异步入库，支持 MyBatis 查询
- 请求 traceId、播放日志和审计日志关联
- 基线安全响应头、CORS 白名单、生产配置 fail-fast
- Actuator / Micrometer 指标端点

## 不包含

- 自助注册、完整 RBAC、多管理员管理
- 课程审核、课程状态机
- 评论、评分、问答、公告、站内信
- 收藏、报名、积分、排行榜、打卡
- 充值余额、积分购买和混合支付
- 微信支付（支付宝电脑网站支付已接入）
- 封面、课件、服务端转码
- CDN

## 运行环境

- Java 17
- Maven 3.9+
- Node.js 18+
- MySQL 8
- Redis 5+
- COS 凭据

## 启动

默认使用数据库 `btry_edu`，后端端口 `8084`，前端端口 `5175`，Actuator 端口 `9091`。

推荐使用一键启停脚本；它会检查已有进程、必要时启动 Redis，并等待健康检查通过：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\start-local.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\stop-local.ps1
```

完整说明见 `docs/本地运行与恢复手册.md`。以下命令用于手工启动或排查启动脚本问题。

如果 Redis 未作为服务运行，先启动临时进程：

```powershell
Start-Process -FilePath 'F:\Redis\redis-server.exe' `
  -ArgumentList 'F:\Redis\redis.windows.conf' `
  -WorkingDirectory 'F:\Redis' -WindowStyle Hidden
```

后端：

```powershell
cd server
mvn spring-boot:run
```

前端：

```powershell
cd web
npm install
npm run dev
```

访问 `http://127.0.0.1:5175`。

## 演示账号

| 账号 | 角色 | 说明 |
|---|---|---|
| `admin` | 管理员 | 账号管理、内容治理、订单查询与退款 |
| `student` / `student_flow` | 学员 | 已分配两个物理系列，用于学习空间与播放验收 |
| `student_pay` | 学员 | 无课程权益，用于课程商店下单与真实支付验收 |
| `teacher` | 教师 | 教师工作台验收账号，拥有两个物理系列 |

密码不写进仓库。全新数据库首次启动会创建 `admin` / `teacher` / `student` 三个演示账号，初始密码取 `SEED_ADMIN_PASSWORD` / `SEED_TEACHER_PASSWORD` / `SEED_STUDENT_PASSWORD`；留空则随机生成并在启动日志打印一次，三个账号都强制首次改密。已有账号不会被重置，改密走登录改密或管理员「账号管理」。公开演示环境的管理员密码必须与本地不同，并定期轮换。

生产演示（`https://whereri.site/learn/`）两个系列均在售，价格统一 `¥0.01`：这是支付宝接口允许的最小金额，服务费按 0.6% 四舍五入到分后为 0，退款也不产生手续费损失。个人账号无营业执照档的额度是单笔 ≤50 元、单日 ≤1000 元。

管理员登录后进入 `http://127.0.0.1:5175/admin/users`，可以新建账号、筛选账号、启停账号、重置密码和分配系列。

已分配课程的学员登录后会看到：

- 物理必修一
- 物理必修二

每个系列包含两章，每章 3 节视频。

`student_pay` 默认没有课程权益，可用于完整验证：

1. 进入课程商店；
2. 创建订单；
3. 点击「前往支付」：生产整页跳转支付宝收银台，本地进入 Mock 收银台；
4. 完成支付（本地可模拟成功/失败/丢回调/重复通知等场景）；
5. 回跳订单页，轮询确认后进入学习空间；
6. 管理员在订单管理中发起全额退款。

## 配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USER` / `DB_PASSWORD` | `root` / `root` | MySQL 账号 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 地址 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `DB_POOL_SIZE` / `DB_POOL_MIN_IDLE` | `10` / `2` | Hikari 连接池 |
| `JWT_SECRET` | 内置开发密钥 | 生产必须覆盖 |
| `CORS_ALLOWED_ORIGINS` | 本地 5175 | 允许的前端来源，逗号分隔 |
| `TRUST_FORWARDED_HEADERS` | `false` | 仅可信反向代理场景开启 |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_IP_MAX_ATTEMPTS` | `8` / `40` | 用户名+IP 与 IP 总量阈值 |
| `LOGIN_WINDOW_MINUTES` | `10` | 登录失败计数窗口 |
| `LOG_FILE` | `logs/btry.log` | 日志文件 |
| `APP_BASE_URL` | `http://127.0.0.1:5175` | 教师邀请链接的前端基础地址 |
| `TEACHER_INVITE_EXPIRY_HOURS` | `72` | 教师邀请有效期 |
| `TEACHER_INVITE_MAX_ATTEMPTS` / `TEACHER_INVITE_WINDOW_MINUTES` | `20` / `10` | 邀请公开接口 IP 限流 |
| `UPLOAD_MAX_SIZE_BYTES` | `4294967296` | 单视频上传大小限制 |
| `UPLOAD_URL_TTL_MINUTES` | `60` | COS 上传地址有效期 |
| `UPLOAD_ALLOWED_CONTENT_TYPES` | `video/mp4,video/webm` | 允许的视频格式 |
| `PAYMENT_PROVIDER` | `mock` | `mock` / `alipay`，生产必须 `alipay` |
| `PAYMENT_MOCK_ENABLED` | `true` | 是否开放 Mock 收银台 |
| `PAYMENT_MOCK_SECRET` | 内置开发密钥 | Mock 支付事件签名密钥，生产必须为空或替换 |
| `PAYMENT_ORDER_EXPIRE_MINUTES` | `15` | 订单支付有效期 |
| `PAYMENT_QUERY_AFTER_SECONDS` | `15` | 支付中订单进入主动查单的等待时间 |
| `PAYMENT_RECONCILE_INTERVAL_MS` | `15000` | 补偿任务执行间隔 |
| `PAYMENT_REFUND_MAX_ATTEMPTS` | `5` | 退款失败自动重试最大次数 |
| `PAYMENT_REFUND_RETRY_BASE_SECONDS` | `30` | 退款重试基础退避秒数 |
| `ALIPAY_APP_ID` | 无 | 开放平台应用 ID，provider=alipay 时必填 |
| `ALIPAY_PRIVATE_KEY` | 无 | 应用私钥，PKCS8 纯 Base64，一行不带头尾 |
| `ALIPAY_PUBLIC_KEY` | 无 | 支付宝公钥（不是应用公钥），用于回调验签 |
| `ALIPAY_SELLER_ID` | 无 | 商家 PID，留空则回调跳过 seller_id 校验 |
| `ALIPAY_GATEWAY_URL` | `https://openapi.alipay.com/gateway.do` | 支付宝网关 |
| `ALIPAY_NOTIFY_URL` | 无 | 公网 HTTPS 异步通知地址 |
| `ALIPAY_RETURN_URL` | 无 | 支付完成回跳地址，网关再拼 `/订单号` |
| `COS_BUCKET` | `rag-1325750042` | COS 桶 |
| `COS_REGION` | `ap-shanghai` | COS 区域 |
| `COS_SECRET_ID` / `COS_SECRET_KEY` | 无 | 未设置时读取本机 `~/.cos.conf` |
| `COS_CONNECT_TIMEOUT_MS` | `3000` | COS 连接超时 |
| `COS_READ_TIMEOUT_MS` | `15000` | COS 读取超时 |
| `COS_MAX_RETRIES` | `2` | COS 最大重试次数 |
| `SAMPLE_VIDEO_KEY` | `edu/video/91/demo-real-181s-faststart-cache.mp4` | 示例视频对象键 |
| `SAMPLE_VIDEO_DURATION_SECONDS` | `181` | 示例视频时长 |
| `PLAYBACK_URL_CACHE_MARGIN_MINUTES` | `30` | URL 缓存提前失效余量 |
| `PLAYBACK_LOG_RETENTION_DAYS` | `30` | 播放日志保留天数 |
| `MANAGEMENT_PORT` | `9091` | Actuator 端口 |
| `ALERT_ENABLED` | `true` | 是否开启主动告警 |
| `ALERT_WEBHOOK_URL` | 空 | 告警 webhook 地址 |
| `ALERT_WINDOW_MINUTES` | `10` | 告警统计窗口 |
| `ALERT_ERROR_THRESHOLD` | `5` | 错误告警阈值 |
| `ALERT_STALL_THRESHOLD` | `10` | 卡顿告警阈值 |
| `ALERT_DEDUP_MINUTES` | `30` | 同类告警去重时间 |
| `ALERT_PAYMENT_PAID_UNFULFILLED_MINUTES` | `1` | 已支付未履约告警阈值 |
| `ALERT_PAYMENT_PAYING_STUCK_MINUTES` | `15` | 支付中订单堆积阈值 |
| `ALERT_PAYMENT_REFUND_RETRY_EXHAUSTED_THRESHOLD` | `1` | 退款重试耗尽告警阈值 |

## 目录

```text
server/
  src/main/java/com/wherelee/edu/
    EduApplication.java
    modules/
      identity/    登录、用户、JWT、限流、教师邀请
      course/      系列、章节、视频、教师内容与发布状态
      learning/    学习空间、学习进度、播放日志
      commerce/    课程商店、订单、支付、退款、权益
      operations/  管理员编排、订单运维、告警
      platform/    AOP、审计、通用 Web、配置、COS/Redis 适配
  src/main/resources/
    db/migration/  Flyway
    mapper/        MyBatis XML
    application.yml
web/src/
  features/
    identity/   登录、改密、教师邀请
    operations/ 管理员账号、邀请、内容和订单
    course/     教师工作台和系列编辑器
    learning/   学习空间和系列播放
    commerce/   课程商店、订单和 Mock 收银台
  shared/api/   HTTP 客户端
  components/   公共组件
  store/        登录态
  utils/        密码策略
```

模块依赖边界和本次审查结论见 `docs/btry完成度审查与模块化改造方案.md`；解耦设计见 `docs/模块解耦设计计划.md`；当前进度见 `docs/模块解耦实施清单.md`。
后端岗位能力调研见 `docs/后端岗位能力调研与btry补齐建议.md`。

## 账号与权限

当前采用单角色模型：`ADMIN / TEACHER / STUDENT`。管理员创建学员账号；教师通过管理员生成的一次性邀请链接设置密码并激活，不开放公开注册。完整 RBAC、权限矩阵和动态菜单暂不引入，迁移条件和路径见 `docs/账号与权限演进方案.md`。

账号相关的工程约束：

- JWT 只携带用户 ID 和 token 版本，账号状态以数据库为准。
- 禁用、重置密码、用户改密都会增加 `token_version`，旧 token 立即失效。
- 待改密账号只能访问改密和当前用户接口。
- 管理员写操作使用 `@RequireRole`、`@AdminAudit`、`@Measured`、`@RateLimit`、`@Idempotent`。
- 教师邀请 token 只保存哈希，链接一次性、限时，邀请只能创建 `TEACHER`。
- 教师系列查询强制校验 `teacher_id`，不能访问他人系列。
- 幂等键对参数做 SHA-256，密码不会进入 Redis key。
- 登录按用户名 + IP 使用 Redis Lua 原子限流；Redis 故障时放行并记录告警。
- 每个请求生成 `X-Trace-Id`，播放日志和审计日志保存 `trace_id`。
- 用户列表使用 MyBatis 动态 SQL，系列分配在事务内先校验后替换。

生产配置、部署、备份恢复和故障处置见 `docs/生产运维手册.md`。
Docker 本地/staging 运行与云服务器验证见 `docs/Docker运行与云服务器验证.md`。

## 订单与 Mock 支付

- 管理员在内容管理中设置价格并开启销售。
- 学员从课程商店创建订单，进入 Mock 收银台完成模拟支付。
- 订单、支付尝试、支付事件和退款分别入库。
- 支付成功先确认 `PAID`，再生成权益并推进到 `FULFILLED`。
- 后台补偿任务负责主动查单、超时关单、补履约和退款重试。
- Mock 事件带 HMAC 签名，并经过与未来真实支付相同的确认和履约服务。
- `prod` profile 下检测到 Mock 支付会直接启动失败。

方案与故障矩阵见 `docs/订单与Mock支付实施方案.md`，验收结果见 `docs/订单与Mock支付验收记录.md`。
真实支付宝准备与后续改造计划见 `docs/支付宝真实支付接入指引与计划.md`。

本地冒烟脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\payment-smoke.ps1
```

脚本需要显式传入 `-AdminPassword` 与 `-BuyerPassword`（密码不写进仓库），买家默认 `student_pay`，覆盖支付成功、失败、丢回调、重复通知、履约失败补偿和退款失败重试。`-IncludeAmountMismatch` 会额外生成一笔需要人工处理的异常订单。

## 质量门禁

本地验证：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools\verify.ps1
```

CI 会执行后端 `mvn test` 和前端 `npm ci && npm run build`，见 `.github/workflows/ci.yml`。

数据库备份与恢复：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools\backup-mysql.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File tools\restore-mysql.ps1 `
  -BackupFile "C:\backups\btry_edu-20261102-030000.sql" -Force
```

恢复脚本要求显式 `-Force`，禁止误覆盖。

## 视频预处理

新视频先做 faststart：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools\prepare-video.ps1 `
  -Source "C:\path\input.mp4" `
  -Destination "C:\path\faststart.mp4" `
  -Ffmpeg "C:\path\ffmpeg.exe"
```

详细上传、元数据和数据库更新步骤见 `docs/视频预处理流程.md`。

## 播放日志

播放相关事件写入 `t_playback_log`，包括后端 URL 请求、缓存命中、前端播放、暂停、拖动、缓冲、错误和结束事件。

- 常用排查 SQL：`docs/视频播放调研与实施方案.md`
- 默认保留 30 天，通过 `PLAYBACK_LOG_RETENTION_DAYS` 配置
- 统计视图：`v_playback_daily`、`v_playback_url_cache_daily`
- 当前用户最近日志：`GET /api/playback/logs?limit=50`
- 支持按事件类型过滤：`GET /api/playback/logs?eventType=ERROR&limit=50`

日志通过有界队列异步批量写入；队列满时会丢弃并记录指标。

## Actuator 与指标

- 健康检查：`http://127.0.0.1:9091/actuator/health`
- Prometheus 指标：`http://127.0.0.1:9091/actuator/prometheus`
- 当前不安装 Prometheus/Grafana，仅暴露指标端点

主要指标：

- `playback.url.cache`：local_hit / redis_hit / miss / lock_wait / lock_timeout / redis_error
- `playback.url.cache.local.size`
- `playback.url.presign`
- `btry.method`
- `playback.log.queue.size`
- `playback.log.persisted`
- `playback.log.persist.errors`
- `playback.log.dropped`
- `security.login.blocked`
- `infra.redis.fallback`
- `infra.redis.local.rate_limit.size`
- `infra.redis.local.fallback_cache.size`
- `playback.audit.failures`
- `payment.orders.submitted`
- `payment.order.paid`
- `payment.order.pay_failed`
- `payment.order.fulfilled`
- `payment.callback.duplicate`
- `payment.callback.invalid`
- `payment.callback.amount_mismatch`
- `payment.fulfillment.failed`
- `payment.refund.succeeded`
- `payment.refund.failed`

## 主动告警

- 告警记录表：`t_alert_log`
- 5 分钟评估一次，窗口默认 10 分钟
- Redis 去重，避免重复推送
- 未配置 `ALERT_WEBHOOK_URL` 时仍然入库，`delivery_error` 记录原因

## Redis 说明

- URL 缓存使用 Redis 作为 L2，本地 `ConcurrentHashMap` 作为 L1。
- 登录/公开接口/通用限流在 Redis 不可用时切换 Caffeine 本地固定窗口，超限仍返回 429。
- `@Idempotent` 使用本地 Caffeine 作为 Redis 故障兜底；资金唯一性仍由 MySQL 保证。
- 告警去重同样有本地 TTL 兜底，极端情况下允许重复告警。
- readiness 只要求 DB，Redis 作为可降级依赖单独展示。
- 降级次数通过 `infra.redis.fallback{component,operation}` 暴露。
- Redis 服务启动异常时，可用上面的 `Start-Process` 方式启动临时进程。

完整边界见 `docs/Redis降级与MQ边界.md`。当前不引入 MQ；RocketMQ 仅在后续需要把支付履约彻底异步化时考虑。

## 视频并发压测

后端和前端启动后，可运行：

```powershell
$env:CONCURRENCY='100'
$env:RANGE_BYTES='1048576'
node tools\video-load-test.mjs
```

结果会输出到 `logs/video-load-test-*.json`。

## 当前说明

- v1 验收结论见 `docs/v1验收记录.md`，本地启停与备份恢复见 `docs/本地运行与恢复手册.md`。
- Mock 订单与支付验收见 `docs/订单与Mock支付验收记录.md`，扩展边界见 `docs/订单与Mock支付实施方案.md`。
- 前端视觉、Element Plus 使用方式和 AI 前端问题规避清单见 `docs/前端界面与组件规范.md`。
- COS 对象使用 `edu/video/91/demo-real-181s-faststart-cache.mp4`，已设置 `Cache-Control: private, max-age=3600`。
- 原对象 `edu/video/91/demo-real-181s.mp4` 和中间缓存对象 `edu/video/91/demo-real-181s-cache.mp4` 保留。
- faststart 重封装已完成，当前对象已把 `moov` 放到文件头。

## 工程验证

- 后端工程深度优化实施计划见 `docs/后端工程深度优化实施计划.md`。
- 订单链路 SQL 与索引审计见 `docs/订单链路SQL与索引审计.md`。
- 并发、幂等与故障恢复测试矩阵见 `docs/并发与幂等测试矩阵.md`。
- JVM/GC 基线见 `docs/JVM与GC基线报告.md`。
- k6 压测与容量结论见 `docs/压测报告.md`。
- OpenTelemetry 接入与排障见 `docs/链路追踪接入与排障.md`。
- Prometheus/Grafana 面板与告警边界见 `docs/监控面板与告警.md`。
- Docker/staging、集成测试库、观测栈和压测入口见 `docs/Docker运行与云服务器验证.md`。
- 支付链路日志字段与真实支付排障见 `docs/支付链路日志与排障.md`。

## 视频 v1 状态

**FROZEN（2026-11-02）**

- 视频播放、Redis URL 缓存、faststart、异步日志、Metrics、告警和故障测试已完成。
- 真实 10～40 分钟视频、真实 500 人播放和浏览器 Network 缓存验证等待外部条件。
- 只有出现真实卡顿、异常流量、持续错误或明确多码率需求时，才重新打开视频部分。
- 当前不引入 HLS、分段缓存、CDN 或 RocketMQ。
