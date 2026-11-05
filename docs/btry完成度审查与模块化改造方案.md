# btry 完成度审查与模块化改造方案

更新日期：2026-11-05

## 1. 结论

btry 的核心业务闭环已经完成：学员登录后进入学习空间，查看已分配系列并按章节观看 COS 视频；教师维护自己的系列、章节和视频；管理员管理账号、教师邀请、课程销售和订单退款；订单、支付宝/Mock 支付、权益发放和补偿链路已经落地。

当前更准确的状态是：**内部试运行可用，正式生产前还需要收口若干订单状态、缓存正确性、演示账号和运维问题。**

本次审查没有改变业务代码。以下结论基于当前 `main` 分支、后端测试、前端构建和生产健康检查。

## 2. 已验证内容

- 后端 `mvn test`：39 个测试类、127 个测试通过，0 失败、0 错误。
- 前端 `npm run build`：生产构建通过。
- 生产 `https://whereri.site/api/health`：返回 `200 {"status":"ok"}`。
- 生产 `https://whereri.site/learn/`：返回 200。
- 本地数据库：5 个账号、2 个系列、4 个章节、12 个视频、1 笔 Mock 订单，暂无退款记录。
- 支付宝、Redis 降级、播放 URL 缓存、订单补偿、退款和权益撤回应有对应代码与测试。

## 3. 已发现的问题

### 3.1 登录页旧演示口令（已修复，见第 8 节）

`web/src/views/Login.vue` 的演示账号区域仍写死旧账号和旧口令，且线上 `Login-BSuC3YfU.js` 仍包含这些字符串。这与最新安全提交“演示账号初始密码不再写死在仓库”不一致。即使线上口令已经轮换，也必须移除；如果某个环境未完成轮换，则属于直接泄露。

同时，演示账号存在多套口径：

- 全新数据库实际创建 `admin`、`teacher`、`student`；
- README 还提到 `student_flow`、`student_pay`；
- 登录页展示 `student_shop`；
- 本地数据库存在 `student_shop`，没有 `student_pay`。

应统一为一份账号清单，并让初始化、README、登录页和验收脚本引用同一来源。

### 3.2 退款后活跃订单键未释放（已修复，见第 8 节）

`OrderRepository.markRefunded` 和 `markRefundFailed` 没有清空 `active_key`。从 `PAID` 状态直接退款时，订单已经不再可支付，但 `active_key` 仍占着唯一索引，用户再次购买同一系列可能被挡住。

### 3.3 过期 `PAYING` 订单关闭不完整（已修复，见第 8 节）

`findExpired` 会选出 `PAYING`，但 `close` 只处理 `CREATED` 和 `PAY_FAILED`。如果用户点击支付后没有真正打开/完成支付宝收银台，支付宝侧交易可能不存在，主动查单长期为 `PENDING`，订单会卡在 `PAYING`。

### 3.4 Redis URL 缓存的本地新鲜期计算不准确

从 Redis 读取缓存时，`PlaybackUrlCache` 把本地 `freshUntil` 设为 `now + freshFor`，没有按 URL 的实际 `expiresAt` 收敛。进程重启后如果命中临近过期的 Redis URL，可能继续返回已经过期的播放地址。

### 3.5 种子数据会在每次启动时重新分配 student 课程

`DataInitializer` 每次启动都执行 `student` 的系列分配，管理员移除后重启会恢复。系列标题缺失时，`idByTitle` 也可能让启动失败。

### 3.6 文档与实现有历史残留

- `docs/项目完整度与后续功能计划.md` 仍写“支付明确不涉及”；
- `docs/支付宝真实支付接入指引与计划.md` 仍写“当前项目没有支付宝回调入口”；
- `docs/上云与后续演进计划.md` 仍写“尚未执行上云”；
- `docs/v1验收记录.md` 和 `docs/本地运行与恢复手册.md` 是历史阶段记录，不能作为当前支付状态依据。

### 3.7 运维收口项

- 生产部署脚本没有写入 `ALERT_WEBHOOK_URL`，告警主要入库，没有外部推送；
- 原先 Linux 服务器没有定时备份单元；现已增加 `btry-backup.timer`、备份脚本和日志轮转配置。
- `stdout.log`/`stderr.log` 没有看到 logrotate；
- 真实支付宝支付和退款还需要一次生产低金额验收证据。

## 4. 模块化目标

本方案采用**单体应用内的逻辑模块化**，不拆微服务。

原因：

- 当前是 10～20 人规模的内部系统，拆微服务会增加部署、网络、事务和排障成本；
- 目标是让代码结构体现业务边界，降低后续修改的影响范围；
- Spring Boot 仍然是单一进程、单一数据库、单一部署单元；
- 未来如果某个模块真的需要独立部署，再从逻辑模块抽出即可。

### 4.1 目标目录

```text
com.wherelee.edu
  EduApplication.java
  modules
    identity
      api
      auth
      user
      security
      invitation
    course
      api
      governance
      series
      teacher
    learning
      api
      learning
      playback
    commerce
      api
      order
      payment
      entitlement
    operations
      admin
      alert
    platform
      aop
      audit
      alert
      common
      config
      resilience
      storage
      web
```

### 4.2 前端结构

前端按功能域组织，路由 URL 保持不变：

```text
web/src
  features
    identity    login / password / invite
    operations  admin users / invitations / content / orders
    course      teacher series / editor / detail
    learning    learning space / series player
    commerce    store / orders / order detail / mock cashier
  shared/api    HTTP 客户端
  components    公共组件
  store         登录态
  utils         密码策略
```

### 4.3 模块职责

- `identity`：登录、用户、JWT、角色校验依赖的安全能力、教师邀请。
- `course`：系列、章节、视频、教师内容维护和发布状态。
- `learning`：学员学习空间、学习进度、播放日志。
- `commerce`：课程商店、订单、支付、退款、权益和履约。
- `operations`：管理员编排接口、订单运维、告警。
- `platform`：AOP、审计存储、通用异常和 Web 基础设施、配置、Redis/COS 适配、健康检查。

### 4.3 依赖方向

允许：

- 所有模块可以依赖 `platform`；
- `course` 可以依赖 `learning` 的播放读模型；
- `commerce` 可以依赖 `platform`；
- `operations` 可以依赖 `identity`、`commerce`、`platform`；
- `identity` 可以依赖 `platform`。

禁止新增：

- `platform` 反向依赖业务模块；
- `learning` 反向依赖 `course` 或 `operations`；
- `commerce` 反向依赖 `operations`；
- 模块之间直接访问对方的 Controller；
- 跨模块共享可变全局状态。

现有跨模块依赖会在迁移时保留; 后续如果继续演进，再通过应用服务或领域事件解耦。

## 5. 迁移原则

1. 不改变 URL、请求/响应结构、数据库表结构和 Flyway 版本。
2. 不改变业务行为；先做包移动和引用修正，再做独立缺陷修复。
3. 测试包跟随主代码移动，保持 package-private 测试可访问。
4. MyBatis XML namespace、resultType、`@MapperScan` 同步更新。
5. 每个模块增加 `package-info.java`，写清职责和允许依赖。
6. 迁移后必须通过 `mvn test` 和 `npm run build`。
7. 不在本次迁移中引入消息队列、CDN、HLS、微服务或完整 RBAC。

## 6. 验收标准

- 旧包名不再出现在主代码、测试、MyBatis XML 和启动扫描配置中；
- 新目录按业务模块可读；
- 所有既有测试通过；
- 前端生产构建通过；
- 本地启动路径和生产部署入口不变；
- README 和架构文档同步更新。
## 7. 本次实施状态

已完成包结构迁移：

- 主代码按 `identity / course / learning / commerce / operations / platform` 六个模块组织；
- 测试目录跟随迁移；
- MyBatis XML namespace、resultType 和 `@MapperScan` 已同步；
- `AdminAuditRepository`、`AlertLogRepository`、`SecurityProperties` 下沉到 `platform`，避免平台层反向依赖业务模块；
- 六个模块增加 `package-info.java` 说明职责和依赖方向；
- README 的目录说明已更新。

验证结果：

- `mvn clean test`：37 个测试类、124 个测试通过；
- `npm run build`：通过；
- 主代码和测试代码的 package 声明与目录路径一致；
- 旧包名不再出现在主代码、测试、MyBatis XML 和启动扫描配置中。
- 本次只调整后端逻辑模块；前端仍保留 `views/api/store` 结构，避免把架构迁移和界面改动混在一起。
- 已进一步落地 `AlertPublisher`、`PlaybackUrlProvider`、`PlaybackEventPublisher` 和 `AdminOrderOperations` 四个契约，并用 ArchUnit 固化边界。
- 详细的下一步任务见 `docs/模块解耦实施清单.md`。
## 8. 订单状态修复记录

2026-11-05 已修复以下 commerce 订单边界：

- `markRefunded` 和 `markRefundFailed` 现在清空 `active_key`，退款终态不再阻塞用户重新购买。
- `findExpired` 不再把 `PAYING` 当作普通超时订单直接关闭。
- 支付宝查单明确返回 `ACQ.TRADE_NOT_EXIST` 时映射为 `NOT_FOUND`；本地订单过期后通过 `closeAfterProviderNotFound` 安全关闭并释放 `active_key`。
- 普通网络/接口故障仍按未知状态处理，不会误关可能已支付的订单。
- 新增 `PaymentServiceTest` 和支付宝查单测试覆盖该路径。
- 终态支付尝试不再被 `PAYING` 订单复用；`REFUND_FAILED` 不再被管理员普通关单接口误导为已关闭。
- 新增 Flyway V12：退款增加 `attempt_count` 和 `next_retry_at`，退款失败按有界退避自动重试。
- 登录页旧演示口令已移除；支付宝回调增加 64KB 请求体上限；新增支付告警巡检和退款重试状态展示。
- 新增 Linux 备份脚本、systemd timer 和日志轮转配置，部署脚本已接线。