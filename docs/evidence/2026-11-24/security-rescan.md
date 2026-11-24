# 依赖复扫与 MODERATE 清理（2026-11-24）

## 变更

- `commons-compress 1.25.0 → 1.26.0`
- `commons-lang3 3.17.0 → 3.18.0`
- `log4j-api 2.24.3 → 2.25.5`
- 引入 RabbitMQ 后新增 `amqp-client 5.25.0`，扫描发现 4 个 HIGH；
  已通过 dependencyManagement 强制升级到 `5.34.0`。

## 复扫结果

- 227 个依赖，14 条 finding / 13 个唯一 advisory。
- 严重度：11 MODERATE，2 CRITICAL（Spring WebMVC SSE/XsltView，已记录为不适用并接受），0 HIGH。
- 相比上一轮：commons-compress、commons-lang3、log4j-api 告警消失；
  RabbitMQ client 的 4 个 HIGH 在升级后消失。
- 剩余 MODERATE 主要为 OpenTelemetry、POI、BouncyCastle 等传递依赖，
  升级需跨大版本验证，暂记录为后续任务。

## CI 门禁

- `.github/workflows/ci.yml` 增加 OSV 扫描，`--min-severity HIGH`。
- `osv-scanner.toml` 仅忽略两条已评估的 Spring advisory（2027-06-30 到期），
  其余 HIGH/CRITICAL 仍会阻断。

## 验证

- 单元测试：245/245。
- 集成测试：101/101。
- 后端镜像重建并部署，readiness healthy，`/api/health` 200。
- RabbitMQ 三个队列消费者均在线。
