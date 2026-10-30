# 网课平台基座

一个录播课平台的后端底座：**用户与认证、RBAC 权限、讲师档案与学科、公告、站内信、系统配置、审计日志**。不含课程、购买、播放——那些是下一阶段的业务。

技术栈：Java 17 · Spring Boot 3.3 · 纯 MyBatis（手写 SQL）· MySQL 8 · Redis · Flyway · JWT。

---

## 一、能做什么

| 能力 | 说明 |
|---|---|
| 注册 / 登录 / 登出 / 改密 | JWT；登出只作废当前设备，改密踢掉所有设备 |
| RBAC 权限 | 5 张表：用户、角色、权限、用户-角色、角色-权限，一人可多角色 |
| 讲师档案 | 独立档案表（真实姓名、头衔、简介、教龄）+ 学科多对多 |
| 讲师审核 | 提交 → 待审 → 通过（同一事务里加 TEACHER 角色）或驳回 |
| 公告 | 草稿 / 发布 / 下架，支持置顶；前台只看已发布 |
| 站内信 | 指定收件人或按角色群发，收件箱、已读、未读数 |
| 系统配置 | 站点名称、客服联系方式、注册开关等 |
| 审计日志 | 关键管理动作自动留痕：谁、何时、对什么、成功与否、耗时 |

## 二、快速开始

### 1. 准备

- MySQL 8（默认 `127.0.0.1:3306`，root/root，可用 `DB_USER`/`DB_PASSWORD` 覆盖）
- Redis（默认 `127.0.0.1:6379`，可用 `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD` 覆盖）

```sql
CREATE DATABASE edu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

表结构和种子数据由 Flyway 在启动时自动执行，不需要手工建表。

### 2. 启动

```bash
cd server
mvn spring-boot:run
```

监听 `http://127.0.0.1:8082`。

### 3. 默认账号

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | 拥有全部 7 个权限 |

> 管理员账号由 `DataInitializer` 在首次启动时创建——BCrypt 哈希必须在运行时生成，写不进 SQL 种子文件。

### 4. 验证存活

```bash
curl http://127.0.0.1:8082/api/health
```

## 三、接口一览（34 个）

**账号**
`POST /api/auth/register` · `POST /api/auth/login` · `POST /api/auth/logout` · `PUT /api/auth/password` · `GET /api/auth/me`

**用户管理**（`user:manage`）
`GET /api/admin/users` · `PUT /api/admin/users/{id}/status` · `PUT /api/admin/users/{id}/roles`

**角色权限**（`role:manage`）
`GET /api/admin/roles` · `GET /api/admin/permissions` · `GET /api/admin/roles/{id}/permissions` · `PUT /api/admin/roles/{id}/permissions`

**讲师**
`POST /api/teacher/apply` · `GET|PUT /api/teacher/profile` · `GET /api/teachers/{id}`
`GET /api/admin/teachers`（`teacher:review`）· `PUT /api/admin/teachers/{id}/review`（`teacher:review`）

**学科**
`GET /api/subjects`（公开）· `POST|DELETE /api/admin/subjects`（`subject:manage`）

**公告**
`GET /api/notices`、`GET /api/notices/{id}`（公开）· 增删改在 `/api/admin/notices`（`notice:manage`）

**站内信**
`POST /api/admin/messages`（`message:send`）· `GET /api/messages` · `GET /api/messages/unread-count` · `GET /api/messages/{id}` · `PUT /api/messages/{id}/read` · `PUT /api/messages/read-all`

**配置与审计**
`GET /api/admin/configs`、`PUT /api/admin/configs/{key}`（`config:manage`）· `GET /api/admin/audit-logs`（`user:manage`）

统一响应体：`{ code, message, data, timestamp }`，`code = 0` 为成功。HTTP 状态恒为 200，业务结果看 `code`。

## 四、几个关键设计

**权限不入 token**

JWT 里只有 `userId` 和 `tokenVersion`。角色和权限每次请求查库，缓存在**请求级** ThreadLocal 里（`PermissionService`）。

理由：管理员收回权限必须立刻生效。如果权限写进 token，要等 2 小时过期才失效，等于收不回来。

**两套 token 失效机制**

| 场景 | 机制 | 存储 |
|---|---|---|
| 退出当前设备 | jti 进黑名单，TTL 为 token 剩余有效期 | Redis `edu:jwt:blacklist:{jti}` |
| 改密 / 全设备下线 | `token_version + 1`，旧 token 的 `ver` 对不上 | MySQL `t_user.token_version` |

Redis 挂了降级放行——`token_version` 那道校验不依赖 Redis，改密踢人的能力不受影响。

**讲师审核是一个事务**

通过审核要同时改两件事：档案状态 `APPROVED`、给用户加 `TEACHER` 角色。放同一个事务里，避免"档案通过了但发不了课"。

**索引按查询模式建**

- 主键全部 `BIGINT AUTO_INCREMENT`：聚簇索引顺序插入，避免页分裂
- 联合索引「等值在前、排序在后」：如 `idx_msg_receiver_read_time(receiver_id, is_read, created_at)` 一条索引同时解决过滤和排序
- `t_user_role` 主键 `(user_id, role_id)` 之外**另建 `KEY(role_id)`**：否则"查某角色下所有人"用不上最左前缀
- 低选择性字段（status 只有两三个值）不单独建索引

**避免 N+1**

用户列表要显示每人的角色。做法是一次 `WHERE user_id IN (...)` 查完再在内存里分组，而不是循环里逐个查。

## 五、日志

`logback-spring.xml` 分三个文件：

```
logs/app.log      全量，按天滚动，保留 15 天
logs/error.log    只 ERROR + 堆栈，保留 30 天
logs/audit.log    审计日志独立，保留 90 天
```

**MDC 链路**：`MdcTraceFilter` 为每个请求生成 `traceId`，`JwtAuthFilter` 认证成功后写入 `userId`。日志格式：

```
2026-10-31 00:02:10.156 INFO [http-nio-8082-exec-1] [68e7b8a585bf4ee7] [12] ... - 业务日志
                                          ↑ traceId              ↑ userId
```

同一个 `X-Trace-Id` 也会写进响应头，前端报错时可以直接带回来查。

**SQL 日志**：开发环境打全量（`application.yml` 里 `com.wherelee.edu.mapper: debug`），**生产必须关掉**——防止密码等敏感数据落进日志文件。

## 六、测试

```bash
cd server
mvn test
```

单元测试覆盖 JWT 签发/解析/篡改/密钥过短。

端到端验证脚本在 `tools/` 下（PowerShell，需先启动服务）：

| 脚本 | 验证内容 |
|---|---|
| `auth-test.ps1` | 注册 → 登录 → 改密（旧 token 失效）→ 登出（黑名单）→ 连错 5 次锁定 |
| `rbac-test.ps1` | 权限拦截、角色变更立即生效、禁用账号、不能禁自己 |
| `teacher-test.ps1` | 讲师申请 → 审核 → 自动获得 TEACHER 角色；学科引用保护 |
| `audit-test.ps1` | 审计留痕（含失败操作）、配置读写 |
| `content-test.ps1` | 公告发布/草稿隔离、站内信按角色群发、已读标记 |

## 七、已知限制

1. **没有前端**，接口用 curl 或脚本验证。
2. **没有课程与购买**：`course:*` 权限点已预留，业务未实现。
3. **注册不送积分**：积分体系尚未开始。
4. **邮箱验证未做**：注册后立即可用，没有激活邮件（需要 SMTP，之前踩过 QQ 邮箱进垃圾箱的坑）。
5. **没有接口级限流**：只有登录失败计数，其他接口面对刷量无保护。
6. **审计只在关键管理动作上**：不是所有接口都记。

## 八、目录

```
server/
  src/main/java/com/wherelee/edu/
    common/     统一响应、错误码、异常、分页、MDC 过滤器
    config/     Web 配置、种子数据初始化、系统配置模块
    security/   JWT、黑名单、登录限速、请求上下文、鉴权过滤器
    rbac/       权限注解、切面、权限服务、角色权限管理
    audit/      审计注解、切面、查询
    user/       用户、角色、Mapper、管理接口
    teacher/    讲师档案、学科
    notice/     公告
    message/    站内信
  src/main/resources/
    db/migration/  V1 建表、V2 种子
    mapper/        MyBatis XML
tools/            端到端验证脚本 + 开发辅助
docs/             本地文档（不入库）
```
