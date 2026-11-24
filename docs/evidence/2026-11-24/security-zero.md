# 依赖告警清零复扫（2026-11-25）

## 升级

- `amqp-client 5.34.0 → 5.38.0`
- `opentelemetry-bom 1.49.0 → 1.68.0`（API 与 extension-trace-propagators 同步为 1.68.0）
- 保留上一轮：POI 5.4.0、BouncyCastle `bcprov-jdk18on 1.85`、
  commons-compress 1.26.0、commons-lang3 3.18.0、log4j-api 2.25.5。

## 测试

服务器 `/opt/etry`：

```
单元测试：246/246，Failures 0，Errors 0
集成测试：101/101，Failures 0，Errors 0
```

新增 `AlipaySignatureCompatibilityTest`：用本地生成的 RSA 密钥调用支付宝 SDK 的
`pageExecute` 签名路径，验证 BouncyCastle 1.85 与 `alipay-sdk-java` 兼容；
该测试不发起任何真实网络请求、不产生真实订单或扣款。

## 部署

- 镜像：`sha256:f7e8fadd38515d9ce6865a209f7c2273ff95463e8f7f2bae1dcbb8f970246259`
- 运行镜像内实际版本：
  - `amqp-client-5.38.0.jar`
  - `opentelemetry-api-1.68.0.jar`
  - `opentelemetry-extension-trace-propagators-1.68.0.jar`
  - `poi-ooxml-5.4.0.jar`
  - `bcprov-jdk18on-1.85.jar`
- readiness `UP`，`/api/health` 200，`/learn/` 200。
- RabbitMQ 三个队列各 1 个消费者、0 积压。
- 管理员登录返回 roles/permissions，`SUPER_ADMIN` 与 `user:manage` 正常。

## OSV 复扫

227 个查询，最终仅 2 条 finding：

```
CRITICAL spring-webmvc 6.2.19 GHSA-j9f9-w8pj-32f8
CRITICAL spring-webmvc 6.2.19 GHSA-pc63-qcmh-9cmg
```

两条均为已评估不适用的 Spring WebMVC SSE/XsltView 问题，已在
`osv-scanner.toml` 记录并设置 2027-06-30 到期；其余 HIGH/MODERATE 为 0。

原始结果：

- `backend-osv-zero.json`
- `backend-osv-zero-details.json`

## 边界

- 仍未发起真实支付宝支付/退款；BC 1.85 与 OTel/AMQP 升级后的真实渠道握手属于 P0 外部验收项。
- 若真实支付宝验签出现兼容问题，按“保留 POI/OTel/AMQP，单独回退 BC”原则处置。
