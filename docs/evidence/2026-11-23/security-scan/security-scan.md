# 依赖与镜像安全扫描（2026-11-23）

## 工具与限制
- **Trivy 镜像扫描未能完成**：漏洞库默认 `ghcr.io` 下载超时；`ghcr.nju.edu.cn` 返回 404；Daocloud ghcr 代理拒绝该仓库（`invalid repository`）。
- 回退方案（等效 SCA）：
  - 后端 Maven 依赖 → OSV API 批量查询（`api.osv.dev/v1/querybatch`）+ 详情补全严重度。
  - 后端镜像 Alpine OS 包 → 读取 `/lib/apk/db/installed` 后 OSV 查询（77 个包）。
  - 前端生产依赖 → `npm audit --omit=dev --registry=https://registry.npmjs.org`。
- 建议 CI 增加 Trivy/OSV 扫描 job（GitHub 网络可用），防止回归。

## 结果

### 前端
- `npm audit --omit=dev`：**0 漏洞**（info/low/moderate/high/critical 均为 0）。

### 后端 Alpine OS 包
- 77 个包，OSV：**0 漏洞**。

### 后端 Maven 依赖
- 查询 192 个运行时依赖，**119 个唯一 advisory**：
  - **CRITICAL 12**
  - **HIGH 42**
  - **MODERATE 50**
  - **LOW 15**
- CRITICAL 包：

| 包 | 当前版本 | 来源 | 修复版本 |
|---|---|---|---|
| dom4j:dom4j | 1.6.1 | alipay-sdk-java | 2.0.3 / 2.1.3 |
| io.netty:netty-handler | 4.1.114.Final | Spring Boot BOM | 4.1.137.Final / 4.2.17.Final |
| org.apache.tomcat.embed:tomcat-embed-core | 10.1.31 | Spring Boot BOM | 10.1.35+（8 条 advisory） |
| org.springframework.security:spring-security-web | 6.3.4 | Spring Boot BOM | 6.5.9 / 7.0.4 |
| org.springframework:spring-webmvc | 6.1.14 | Spring Boot BOM | 7.0.9 |

- HIGH 主要集中在：Jackson 2.17.2、Netty 4.1.114、Tomcat 10.1.31、Spring Boot 3.3.5/Actuator、Spring Data Commons 3.3.5、Spring Security Crypto 6.3.4、Micrometer 1.13.6。
- 另有 `org.ini4j:ini4j:0.5.4`（HIGH，来自 tencentcloud-sdk-java-common），**OSV 无修复版本**。

## 整改方案（P1 安全整改）
1. **Spring Boot 3.3.5 → 3.5.14**（OSV 给出的修复线），同步观察 springdoc/MyBatis 兼容性；跑全量单测 + 定向集成，重建镜像并部署。
2. **dom4j 1.6.1**：从 `alipay-sdk-java` 排除，显式引入 `dom4j:2.1.4`；验证支付宝客户端签名/响应解析，纳入后续沙箱/¥0.01 验收。
3. **ini4j 0.5.4**：无修复版本；确认仅用于本地可信 `~/.cos.conf`/配置读取（不接受外部 INI）。若无实际用途则排除；否则记录为**接受风险**并加补偿（隔离配置、最小权限）。
4. 复扫 OSV：目标 **CRITICAL/HIGH = 0**（ini4j 例外需显式记录）；前端与 Alpine 保持 0。
5. CI 增加 SCA（Trivy/OSV），与单测/集成/docker-build 同门禁。

## 证据文件（同目录）
- `backend-osv.json`（原始 findings）
- `backend-osv-details.json`（严重度/修复版本）
- `backend-osv.err`（查询计数）
- `deps.txt`（Maven 运行时依赖清单）
- `os-packages.txt` / `os-packages-osv.json`（Alpine 包与结果）
- `frontend-audit.json`（npm 审计，0 漏洞）

## 说明
- OSV 是 advisory 级别匹配，不代表每条都可被利用；但 12 CRITICAL/42 HIGH 已高于生产基线，必须升级后复扫。
- 当前线上仍运行旧镜像；整改完成前不建议对外宣称“生产安全达标”。
