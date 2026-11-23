# 学习资料商城（etry）

一个面向 K12 的**电子版学习资料商城**：买家自助注册后浏览/搜索资料（试卷、教辅、讲义、课本），加入购物车、下单支付，支付成功后在「资料书架」试读与下载带水印的 PDF；运营端基于 RBAC 权限点管理资料、订单、退款审批、对账与审计。

> 个人面试项目，部署在自有域名下做演示，**不涉及真实交易**。支付内核复用支付宝电脑网站支付，演示默认走本地 Mock 收银台。

## 状态

- 本地运行：`tools\start-local.ps1`
- CI：见 [`.github/workflows/ci.yml`](.github/workflows/ci.yml)（frontend / backend / docker-build 三任务）
- 部署与验收：见 [部署与验收清单-资料商城](docs/部署与验收清单-资料商城.md)
- 开发问题日志：见 [development-log](docs/development-log.md)
- 阶段总结：见 `docs/阶段总结-阶段*.md`

## 功能

### 买家
- 自助注册 / 登录（注册即登录、无需首改密）；注册即授予 `BUYER` 角色。种子账号首次登录强制改密。
- 资料目录：分类筛选、分页、关键词搜索（MySQL ngram 全文索引）、详情。
- 游客试读：前 N 页预览，叠加「试读」水印，无需登录。
- 购物车：多商品加购、结算生成一笔多明细订单。
- 下单支付：支付宝电脑网站支付或本地 Mock 收银台；订单状态机 + 发件箱异步履约。
- 资料书架：已购资料列表、权益门槛下载（叠加购买者水印）；**下载只认权益不认在售状态**，下架不影响已购下载。
- 退款申请：买家发起 → 客服审批（通过即全额退款并回收权益 / 驳回可附理由）→ 驳回后可再次申请。

### 运营端（RBAC 权限点驱动）
- 资料管理：新建 / 编辑 / 上下架 / 推荐 / 挂载 PDF 文件（`material:*`）。
- 订单运维：查单 / 补履约 / 关单（`order:read` / `order:operate`）。
- 退款处理：审批买家退款申请、执行全额退款（`refund:operate`）。
- 财务对账：对账查询与处理、导出（`recon:read` / `recon:operate`）。
- 用户与角色：账号管理、角色授予（`user:read` / `user:manage`）。
- 审计日志：关键运营动作留痕（`audit:read`）。

### 支付与履约内核
- 订单状态机、支付尝试（attempt）、支付事件幂等、发件箱（outbox）异步发放/回收权益。
- 全额退款：状态守卫 + 分布式锁 + 每订单唯一退款行，并发下「恰好一笔退款、权益恰好回收一次、订单终态一致」。
- 支付宝异步通知验签 + 主动查单兜底 + 定时对账。

## 架构

```text
浏览器（Vue 3 + Element Plus）
  ├─ 买家：资料商城 / 详情试读 / 购物车 / Mock 收银台 / 我的订单 / 资料书架
  └─ 运营：资料 / 订单 / 退款审批 / 对账 / 审计 / 用户角色（RBAC）
        │
        ▼
Spring Boot 3（Java 17）
  ├─ identity（注册/登录/JWT/RBAC） / material（目录/交付/管理）
  ├─ commerce（购物车/订单/支付/退款/对账/outbox） / entitlement（权益）
  ├─ operations（运营端） / platform（安全/审计/种子/异常）
  ├─ MySQL 8 + Flyway（V1–V24，utf8mb4 + ngram FULLTEXT）
  ├─ Redis 7：限流 / 幂等兜底 / 缓存
  ├─ 本地 PDF 存储：PDFBox 生成演示资料、试读截页、下载水印
  └─ 支付宝 / Mock 支付
```

- 鉴权：`JwtAuthFilter` 每请求从库读角色+权限点，授予即时生效（同 token）；接口用 `@PreAuthorize("hasAuthority('perm:code')")`，`AccessDeniedException` 统一 403。
- 安全收口：CSP / nosniff / DENY / no-referrer / Permissions-Policy 响应头，HSTS 仅 HTTPS；CORS 白名单；登录限流；弱口令校验。
- 模块解耦：跨模块只经 `api` 包契约（如 `EntitlementQuery`），表归属登记，详见 [模块解耦设计计划](docs/模块解耦设计计划.md)。

## 快速开始

### 前置
- JDK 17、Maven 3.9+、Node.js 18+、MySQL 8、Redis 5+

### 启动
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\start-local.ps1
```
启动后访问 `http://127.0.0.1:5175`。停止：
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\stop-local.ps1
```
完整本地启动、恢复与排障见 [本地运行与恢复手册](docs/本地运行与恢复手册.md)。

### 演示账号与资料
| 账号 |  legacy 角色 | RBAC 授予 | 用途 |
|---|---|---|---|
| `admin` | ADMIN | SUPER_ADMIN（全部权限点） | 运营端全功能 |
| `teacher` | TEACHER | OPERATOR（资料管理） | 资料运营 |
| `student` | STUDENT | BUYER | 买家演示 |
| 自助注册 | — | BUYER | 任意新买家 |

初始密码由 `SEED_ADMIN_PASSWORD` / `SEED_TEACHER_PASSWORD` / `SEED_STUDENT_PASSWORD` 设置；留空时启动日志打印一次随机密码，首次登录强制改密。

首次启动种子 5 份演示资料（PDF 由项目惰性生成，不依赖外部采购）：
- 高中数学函数专项突破试卷（¥9.90，86 页，试读 3 页，推荐）
- 高中英语语法通关教辅（¥15.90，64 页，试读 3 页）
- 小学语文阅读提分讲义（¥6.90，40 页，试读 2 页）
- 高中物理必修一电子课本（¥12.90，120 页，试读 5 页）
- 初中化学实验安全手册（下架、未挂文件，演示「不可上架售卖」约束）

## 配置

| 变量 | 说明 |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | MySQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 地址 |
| `JWT_SECRET` | JWT 签名密钥，生产必须覆盖 |
| `MATERIAL_STORAGE_ROOT` | 资料 PDF 存储根（默认 `./data/materials`；容器内挂 `material-data` 卷持久化） |
| `CORS_ALLOWED_ORIGINS` | 跨域白名单，生产必填 |
| `PAYMENT_PROVIDER` | `mock` 或 `alipay`，生产真实支付须为 `alipay` |
| `PAYMENT_MOCK_ENABLED` | Mock 支付开关，**默认 false（fail-closed）**：`provider=mock` 未显式开启则启动失败；仅演示/测试开启，prod 禁止 |
| `ALIPAY_APP_ID` / `ALIPAY_PRIVATE_KEY` / `ALIPAY_PUBLIC_KEY` | 支付宝应用配置 |
| `SEED_ADMIN_PASSWORD` / `SEED_TEACHER_PASSWORD` / `SEED_STUDENT_PASSWORD` | 演示账号初始密码 |

完整配置见 `server/src/main/resources/application.yml`、生产硬化见 `application-prod.yml`，部署审查见 [部署与验收清单-资料商城](docs/部署与验收清单-资料商城.md)。

## 主要流程

1. 买家注册登录 → 商城浏览/搜索/试读 → 加购物车 → 结算生成多明细订单 → 支付宝/Mock 支付 → 发件箱异步发放权益 → 资料书架下载（购买者水印）。
2. 买家发起退款申请 → 客服审批通过 → 全额退款 + 回收权益（并发安全）；驳回可附理由，买家可再次申请。
3. 运营按 RBAC 权限点管理资料上下架、订单运维、对账导出，关键动作写入审计日志。

## 目录
```text
server/  Spring Boot 后端（identity/material/commerce/entitlement/operations/platform）
web/     Vue 3 前端（features 按域划分）
docs/    设计、运行、支付、部署、验证与阶段总结
tools/   本地脚本、冒烟测试、k6 压测、备份恢复
deploy/  Docker、Compose、Nginx/Caddy、systemd、备份定时任务
```

## 文档
- [部署与验收清单-资料商城](docs/部署与验收清单-资料商城.md)
- [本地运行与恢复手册](docs/本地运行与恢复手册.md)
- [订单与 Mock 支付实施方案](docs/订单与Mock支付实施方案.md)
- [支付宝真实支付接入指引与计划](docs/支付宝真实支付接入指引与计划.md)
- [模块解耦设计计划](docs/模块解耦设计计划.md)
- [账号与权限演进方案](docs/账号与权限演进方案.md)
- [开发问题日志](docs/development-log.md)

## 非目标
- 不做真实交易（演示项目，支付默认 Mock）。
- 分销员体系暂缓。
- 不接 CDN / HLS / 服务端转码（资料为 PDF，非视频）。
- 不做评论、评分、问答、公告、站内信。
- 不接微信支付。

## 已知边界
- 前端运营控制台仍按 legacy 角色（ADMIN/TEACHER/STUDENT）做路由门控，后端按 RBAC 权限点鉴权；二者经种子授予桥接（admin→SUPER_ADMIN 等）。
- 遗留课程模块（series/video/teacher-invitation）控制器与路由尚存，待后续退役。
- 真实支付宝 ¥0.01 支付验收需域名/服务器/商户（或沙箱）凭据，属外部阻塞，云端执行延后。

## License
个人项目，暂未指定开源许可证；如需复用请联系作者。