# 支付宝真实验收 Runbook（待用户选择沙箱或 ¥0.01）

## 前置确认（必须先由用户拍板）

1. 验收方式：支付宝沙箱，或生产环境真实 ¥0.01。
2. 维护窗口：低峰时段，期间不发布其他变更。
3. 生产 `.env.docker` 的 `ALIPAY_*` 与所选环境一致；不要把密钥输出到终端或证据文件。
4. 当前 `PAYMENT_PROVIDER=alipay`、`PAYMENT_MOCK_ENABLED=false`；不要在验收时打开 Mock。
5. 已通过不联网的 `AlipaySignatureCompatibilityTest`，确认 BC 1.85 下
   `pageExecute` 可签名；真实网关握手仍需本次验收确认。

## 准备可售商品与临时买家

- 选择一个 `PUBLISHED` 系列，临时设置 `sale_status=ON_SALE`、`price_cents=1`
  （¥0.01）。记录原值，验收后恢复。
- 通过公开注册创建临时买家：
  `POST /api/auth/register`，用户名使用 `alipay_e2e_<timestamp>`，
  密码使用随机强口令并仅保存在本机临时环境变量。
- 不修改 admin/teacher/student 种子账号的首改密状态。

## 验收步骤

1. 买家登录：`POST /api/auth/login`，保存 token。
2. 创建订单：`POST /api/orders`
   ```json
   {"seriesId": <系列ID>, "clientRequestId": "alipay-e2e-<timestamp>"}
   ```
   记录 `orderNo`。
3. 发起支付：`POST /api/orders/{orderNo}/pay`，记录返回的 `cashierUrl` 和 `channel=ALIPAY`。
4. 在浏览器打开 `cashierUrl`：
   - 沙箱：用沙箱买家账号完成支付；
   - 真实：用户本人确认支付 ¥0.01。
5. 等待回调并轮询 `GET /api/orders/{orderNo}`：
   - 期望最终 `status=FULFILLED`；
   - 记录 `paidAt`、`fulfilledAt`、`providerTradeNo`。
6. 检查 `GET /api/auth/me` 权益/学习空间：
   - 对应系列出现在已购列表；
   - 或查 `t_entitlement` 的 `status=ACTIVE`、`source_order_id` 指向该订单。
7. 退款（建议做，沙箱或真实均可）：
   - 管理员先完成首改密或使用已授权的管理账号；
   - `POST /api/admin/orders/{orderNo}/refund`（按现有管理端接口）；
   - 期望订单 `REFUNDED`、权益 `REVOKED`、outbox `REVOKE` DONE。
8. 记录证据：
   - 订单号、渠道交易号、状态时间线、权益状态；
   - 后端支付/回调/退款日志（不含 token、密钥、完整手机号）；
   - 关键页面截图；
   - 写入 `docs/evidence/2026-11-25/` 并在索引中链接。

## 失败处置

- 若签名/验签失败：保留 POI/OTel/AMQP 升级，单独回退 BC 到可用版本并记录原因；
  不建议为了通过验收恢复 BC 1.70 的已知漏洞版本。
- 若回调未达：检查 `notify_url` 公网可达、支付宝应用回调白名单、
  `t_payment_event` 是否落库，再走主动查单补偿。
- 若金额/交易号不一致：不要手工改订单为已支付，按现有对账差异流程处理。

## 清理

- 恢复系列原 `sale_status`/`price_cents`。
- 沙箱订单可保留；真实 ¥0.01 订单建议走退款并保留审计记录。
- 删除临时买家、登录 token 文件与临时环境变量。
