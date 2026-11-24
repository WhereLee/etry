# 依赖安全整改与复扫（2026-11-24）

## 结果对比
- 整改前：192 个运行时依赖，**119 个 advisory（12 CRITICAL / 42 HIGH / 50 MODERATE / 15 LOW）**。
- 整改后：**14 个 advisory（2 CRITICAL / 0 HIGH / 12 MODERATE）**。

## 升级与处置
- Spring Boot `3.3.5 → 3.5.16`（Spring Framework 6.2.19、Spring Security 6.5.11、Spring Data 3.5.13、Micrometer 1.15.12）。
- 显式安全补丁覆盖：Tomcat `10.1.62`、Netty `4.1.137.Final`、Jackson `2.21.7`。
- `dom4j 1.6.1 → org.dom4j:dom4j 2.1.4`（从 alipay-sdk 排除旧版）。
- `ini4j 0.5.4`：从 `cos_api` 排除；应用自行读取 env / `~/.cos.conf`，不依赖 ini4j。
- `okhttp 3.12.13 → 4.12.0`（从 alipay-sdk 排除旧版）。
- `springdoc 2.6.0 → 2.8.9`。

## 剩余与风险处置
- **2 CRITICAL：Spring WebMVC 6.2.19**
  - `GHSA-j9f9-w8pj-32f8`：SSE 流渲染片段损坏。
  - `GHSA-pc63-qcmh-9cmg`：XsltView 路径限制问题。
  - 代码检索确认项目**未使用 SSE / fragment / XsltView / XML 视图**（纯 JSON REST + PDF），两条不适用；OSV 标注修复仅 7.0.9（Spring Boot 4）。
  - 处置：**接受风险**并记录；若未来引入 SSE/XML 视图，需升级 Spring Boot 4 后再上线。
- **12 MODERATE**：Commons Compress、BouncyCastle、OpenTelemetry、Apache POI、Commons Lang、Log4j API 等；不阻塞当前上线，列入后续依赖清理任务。

## 验证
- 单元测试 **240/240**。
- 定向集成 **9/9**。
- 全量集成 **100/100**（含对账、退款并发、outbox、RBAC、安全契约）。
- 后端镜像重建并部署；`/learn/` 200、`/api/health` 200、admin 登录 200、试读 PDF 200；COS 播放初始化正常；无 dom4j/ini4j 运行时错误。
- 支付宝真实支付验收仍待确认（沙箱或 ¥0.01）；dom4j/okhttp 变更将在真实支付链路中复核。

## 附带修复：容器 MySQL 时区
- 现象：全量集成 `ReconciliationIntegrationTest.providerOnlyAndLocalOnlyAreBothReported` 在 00:39 CST 失败，缺 `LOCAL_ONLY`。
- 根因：容器 MySQL 默认 UTC，而应用/JDBC 使用 Asia/Shanghai；跨日时对账日期窗错位。
- 修复：MySQL 启动参数 `--default-time-zone=+08:00`；单测类 6/6、全量 100/100 通过；线上 `@@global.time_zone=+08:00`。

## 工具与限制
- Trivy 漏洞库镜像不可达（ghcr 超时、NJU 404、Daocloud 拒绝），改用 OSV API + npm audit 完成等效扫描；建议 CI 增加 Trivy/OSV job 作为持续门禁。

## 证据
- 整改前：`docs/evidence/2026-11-23/security-scan/`
- 中间复扫：`docs/evidence/2026-11-23/security-scan-after/`
- 最终复扫：`docs/evidence/2026-11-23/security-scan-final/`
