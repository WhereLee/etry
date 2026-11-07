# 学习空间

一个内部录播课程平台：学生登录后浏览课程商店、购买系列，并在学习空间按章节观看视频。

## 状态

- 本地运行：`tools\start-local.ps1`
- 生产演示：`https://whereri.site/learn/`
- CI：见 [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- 验证、压测和运维记录：见 [验证与运维](docs/验证与运维.md)

## 功能

- 学员：课程商店、下单支付、学习空间、断点续播和学习进度。
- 教师：邀请激活、系列/章节/视频管理、COS 直传和发布状态。
- 管理员：账号管理、课程分配、内容治理、订单查询、退款和人工补偿。
- 支付：支付宝电脑网站支付与本地 Mock 收银台，订单状态机、权益发放和全额退款。
- 视频：COS 预签名播放、播放 URL 缓存、播放日志和异常排查。
- 账号：管理员创建学员，教师通过一次性邀请链接激活，不开放自助注册。

## 架构

```text
浏览器（Vue 3）
  ├─ 学员：课程商店 / 学习空间 / 播放器
  ├─ 教师：系列、章节、视频
  └─ 管理员：账号、内容、订单、退款
        │
        ▼
Spring Boot 3（Java 17）
  ├─ identity / course / learning / commerce / operations
  ├─ MySQL 8 + Flyway
  ├─ Redis：播放 URL 缓存、限流、幂等兜底
  ├─ COS：视频上传与预签名播放
  └─ 支付宝 / Mock 支付
```

## 快速开始

### 前置

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8
- Redis 5+
- COS 凭据

### 启动

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\start-local.ps1
```

启动后访问 `http://127.0.0.1:5175`。停止服务：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\stop-local.ps1
```

更完整的本地启动、恢复和排障步骤见 [本地运行与恢复手册](docs/本地运行与恢复手册.md)。

### 演示账号

| 账号 | 角色 | 说明 |
|---|---|---|
| `admin` | 管理员 | 账号、内容治理、订单与退款 |
| `teacher` | 教师 | 系列、章节、视频和发布 |
| `student` / `student_flow` | 学员 | 已分配系列，用于学习空间验收 |
| `student_pay` | 学员 | 无课程权益，用于下单和支付验收 |

初始密码由 `SEED_ADMIN_PASSWORD` / `SEED_TEACHER_PASSWORD` / `SEED_STUDENT_PASSWORD` 设置；留空时启动日志打印一次，首次登录强制修改。生产演示环境两个系列均为 `¥0.01`，用于支付宝真实支付验收。

## 配置

| 变量 | 说明 |
|---|---|
| `DB_USER` / `DB_PASSWORD` | MySQL 账号 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 |
| `JWT_SECRET` | JWT 签名密钥，生产必须覆盖 |
| `COS_BUCKET` / `COS_REGION` | COS 桶和区域 |
| `COS_SECRET_ID` / `COS_SECRET_KEY` | COS 凭据；未设置时读取本机 `~/.cos.conf` |
| `PAYMENT_PROVIDER` | `mock` 或 `alipay`，生产必须为 `alipay` |
| `ALIPAY_APP_ID` / `ALIPAY_PRIVATE_KEY` / `ALIPAY_PUBLIC_KEY` | 支付宝应用配置 |
| `SEED_ADMIN_PASSWORD` / `SEED_TEACHER_PASSWORD` / `SEED_STUDENT_PASSWORD` | 首次启动的演示账号密码 |

完整配置见 `server/src/main/resources/application.yml` 和 [本地运行与恢复手册](docs/本地运行与恢复手册.md)。

## 主要流程

1. 学员在课程商店创建订单，通过支付宝或 Mock 收银台支付，支付成功后获得课程权益并进入学习空间。
2. 教师通过邀请链接激活账号，创建系列、章节和视频，视频直传 COS 后发布。
3. 管理员负责账号、课程分配、内容治理、订单查询、退款和履约补偿。

## 目录

```text
server/  Spring Boot 后端
web/     Vue 3 前端
docs/    设计、运行、支付、视频和验证文档
tools/   本地脚本、冒烟测试和压测工具
deploy/  Docker、Compose 和 Nginx 配置
```

## 文档

- [本地运行与恢复手册](docs/本地运行与恢复手册.md)
- [订单与 Mock 支付实施方案](docs/订单与Mock支付实施方案.md)
- [支付宝真实支付接入指引与计划](docs/支付宝真实支付接入指引与计划.md)
- [视频播放调研与实施方案](docs/视频播放调研与实施方案.md)
- [生产运维手册](docs/生产运维手册.md)
- [验证与运维](docs/验证与运维.md)
- [模块解耦设计计划](docs/模块解耦设计计划.md)
- [账号与权限演进方案](docs/账号与权限演进方案.md)

## 非目标

- 不开放自助注册。
- 不引入完整 RBAC、动态菜单和多管理员体系。
- 不接 CDN、HLS 或服务端转码。
- 不做评论、评分、问答、公告和站内信。
- 不接微信支付。

## License

个人项目，暂未指定开源许可证；如需复用请联系作者。
