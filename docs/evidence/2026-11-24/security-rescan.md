# 依赖升级收口与复扫（2026-11-24）

## 1. 变更

- Spring Boot 3.5.16（含 Tomcat 10.1.62、Netty 4.1.137、Jackson 2.21.7 覆盖）。
- Apache POI 升级到 5.4.0：`poi`、`poi-ooxml`、`poi-ooxml-lite`。
- BouncyCastle 升级到 `bcprov-jdk18on 1.85`。
- 支付宝 SDK 继续使用 `4.40.1067.ALL`，排除其旧 `dom4j`、`okhttp`、`bcprov-jdk15on`，补齐 `dom4j 2.1.4`、`okhttp 4.12.0`。
- 腾讯云 COS SDK 排除 `ini4j` 和 `bcprov-jdk15on`。
- 重新生成 `server/dependency-list.txt`（229 行依赖清单）。

## 2. BouncyCastle 根因修正

首轮升级只从支付宝 SDK 排除了 `bcprov-jdk15on`，但腾讯云 COS SDK
仍传递引入 `bcprov-jdk15on 1.70`。首次重建后的镜像同时包含：

```
BOOT-INF/lib/bcprov-jdk15on-1.70.jar
BOOT-INF/lib/bcprov-jdk18on-1.85.jar
```

这会让 OSV 继续命中旧 BC advisory，也不符合“只保留 BC 1.85”的验收条件。
修复为从 `cos_api` 同样排除 `bcprov-jdk15on` 后：

```
mvn dependency:tree -Dincludes=org.bouncycastle
org.bouncycastle:bcprov-jdk18on:jar:1.85:compile
```

运行镜像实际清单也只包含：

```
BOOT-INF/lib/bcprov-jdk18on-1.85.jar
```

## 3. 测试

在服务器 `/opt/etry` 执行：

```bash
bash tools/server-dev-test.sh all
```

结果（BC 1.85 + POI 5.4.0）：

```
单元测试：245/245，Failures 0，Errors 0，Skipped 0
集成测试：101/101，Failures 0，Errors 0，Skipped 0
```

完整日志保留在服务器：

- `docs/evidence/2026-11-24/test-logs/all-bc85-20261124-222829.log`

该轮测试覆盖支付并发、退款并发、发件箱、防重、RBAC、对账、资料交付和 PDF 生成；
其中 PDF/POI 相关测试均通过。

## 4. 部署与运行时验收

- 使用 `deploy-server.sh` 重建 backend 和前端，部署前自动保留 `btry-backend:prev-*`。
- 运行镜像：`sha256:ffa988517ee67960f192790d423c74c8e5a261f255a9068bccc7b85c43b953a9`。
- 容器状态：`running/healthy`，readiness UP。
- 外网入口：
  - `https://whereri.site/api/health` → 200
  - `https://whereri.site/learn/` → 200
  - 注意：`/learn/api/*` 是 SPA 静态兜底，不是后端 API；API 根路径是 `/api/*`。
- RabbitMQ 三个队列均为 1 个消费者、0 条积压：
  - `btry.outbox.fulfill`
  - `btry.outbox.revoke`
  - `btry.outbox.dlq`
- 管理员登录与 `/api/auth/me` 验证：
  - roles = `SUPER_ADMIN`
  - permissions = 12
  - token 正常返回，未在证据中记录密码或 token。

验收记录见 `postdeploy-verification.txt`。部署日志保留在：

- `docs/evidence/2026-11-24/deploy-logs/deploy-bc85-20261124-223305.log`

## 5. OSV 复扫

使用 `tools/osv-scan.py` 对当前依赖清单查询 OSV，并用 `tools/osv-details.py`
重新获取严重度详情，避免沿用上一轮 8 条 `severity=ERROR`。

结果：

```
依赖行：229
实际查询：227
findings：8
唯一 advisory：7
severity：0 HIGH，5 MODERATE，2 CRITICAL（已知忽略），0 ERROR
```

残留：

- `spring-webmvc 6.2.19`：2 条 CRITICAL（SSE 片段流、XsltView），项目仅使用 JSON REST/PDF，
  已在 `osv-scanner.toml` 记录不适用，忽略到期日 2027-06-30。
- `amqp-client 5.34.0`：3 条 MODERATE，修复版本最高需要 5.36.1；当前 CI 门禁为 HIGH，
  不阻塞，后续单独做客户端兼容验证。
- OpenTelemetry 1.49.0：2 条唯一 MODERATE（baggage 与 tracestate），修复版本分别为
  1.62.0 / 1.67.0；需要独立验证 BOM 兼容后再升级。

本轮确认：

- BC 1.70 的 advisory 已消失。
- BC 1.85 与 POI 5.4.0 无 HIGH/CRITICAL 命中。
- 严重度详情获取无 ERROR。

原始结果：

- `backend-osv-bc85.json`
- `backend-osv-bc85-details.json`

## 6. CI SCA 门禁修复（尚未在 GitHub Actions 实际执行）

只读核对发现原 workflow 的 action 不可用：

- `google/osv-scanner-action` 没有 `v2` tag 或分支，只有 `v2.7.1` 等精确版本。
- `v2.7.1` 根 `action.yml` 没有 `runs`，不能直接 `uses`。
- v2.7.1 CLI/源码没有 `--min-severity` 参数，原 `scan-args` 会被拒绝或误用。

已将 `sca` job 改为可复现脚本链：

1. `mvn dependency:list` 生成当前依赖清单；
2. `tools/osv-scan.py` 查询 OSV batch API；
3. `tools/osv-details.py` 获取严重度详情，网络失败重试且优先 IPv4；
4. `tools/osv-gate.py` 对 HIGH/CRITICAL 做 fail-closed 门禁，读取
   `osv-scanner.toml` 的有效忽略项；详情缺失、`ERROR` 或过期忽略均失败。

负向测试已通过：空忽略配置、过期忽略、详情 `ERROR` 三种情况均返回非零。
GitHub 凭据仍未配置，因此该 job 尚未在 Actions 实际执行。

## 7. 边界与后续

- 本轮没有发起任何真实支付宝扣款，也没有调用真实退款。BC 1.85 与支付宝 SDK 的
  真实签名/验签/退款握手仍属于 P0 外部阻塞项；Mock/集成测试通过不能替代真实渠道验收。
- 若真实支付宝验收发现 BC 1.85 运行时不兼容，按“保留 POI 5.4.0，单独回退 BC”的原则处理，
  不为了漏洞扫描破坏支付链路。
- 未推送 GitHub，CI 的 `sca` job 尚未实际执行；推送凭据仍待用户确认。