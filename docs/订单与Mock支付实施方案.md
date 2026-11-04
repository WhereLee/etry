# 订单与 Mock 支付实施方案

更新日期：2026-11-02

状态：**Mock 订单与支付链路已实现（2026-11-03）；支付宝电脑网站支付已于 2026-11-05 接入，本文保留为 Mock 阶段实施方案**

## 1. 结论

本阶段实现完整订单与课程购买闭环，但支付渠道使用本地 Mock，不接真实支付宝。

Mock 不能只是“点击按钮把订单改成已支付”。它必须模拟支付平台的外部行为，并复用未来真实支付所需的确认、履约、补偿和对账路径。这样后续接支付宝时只替换网关适配器，不重写订单和权益。

### 实施结果

已落地：

- Flyway V11：销售字段、权益字段、订单、支付尝试、支付事件和退款表。
- 管理员定价与销售开关，学员课程商店、订单详情和我的订单。
- Mock 收银台、HMAC 事件签名、支付确认、权益履约和全额退款。
- 主动查单、超时关单、补履约、退款补偿和异常订单视图。
- 支付业务指标、失败告警入库和本地故障冒烟脚本。

未落地：

- 真实支付宝 SDK、沙箱网关和生产商户配置。
- 用户自助退款、发票和智能客服。

## 2. 调研结论

参考：

- 支付宝电脑网站支付快速接入：<https://opendocs.alipay.com/open/270/105899>
- 支付宝异步通知说明：<https://opendocs.alipay.com/support/01raw4>
- 支付宝沙箱环境：<https://opendocs.alipay.com/common/05yvy1>
- Stripe 幂等请求设计：<https://docs.stripe.com/api/idempotent_requests>

关键结论：

- 异步通知必须验签，并核对 `app_id`、`out_trade_no`、`total_amount` 和交易状态，不能只根据“收到请求”更新订单。
- 支付平台可能重复通知、延迟通知或通知丢失，订单状态必须可主动查询和补偿。
- 沙箱与生产环境数据完全隔离，使用独立网关、APPID、密钥和测试账号；生产上线时必须替换完整配置。
- 支付是至少一次通知语义，不是天然“只执行一次”。业务侧必须用数据库唯一约束、状态机和幂等处理保证权益只生成一次。
- 金额必须由后端订单决定，不能接受前端传入的价格。
- 现有 Redis 幂等不能作为资金安全的唯一保障；财务关键唯一性必须落到 MySQL 约束。

## 3. 本阶段范围

### 3.1 默认业务假设

以下作为本方案基线，实施前如无调整即按此执行：

- 购买人是已登录的 `STUDENT` 账号。
- 不新增家长账号，不开放自助注册。
- 一个订单只购买一个系列，数量固定为 1。
- 管理员负责定价和开启销售；教师不能自行改价。
- 价格使用人民币“分”存储，不使用浮点数。
- 学生已经获得权益时不能重复购买。
- 购买获得的权益默认长期有效。
- 管理员手动分配的权益和订单购买的权益分开记录。
- 退款由管理员手动发起，本阶段只做全额退款。
- 已获得权益的学员不因系列后续回到草稿、下架或归档而自动失去访问权；需要收回时必须显式撤销权益并记录审计。
- 新购买只允许 `PUBLISHED + ON_SALE` 且价格大于 0 的系列。

### 3.2 本阶段做

- 系列价格、销售开关和课程商店。
- 权益模型升级，兼容原有管理员分配。
- 订单创建、订单查询、订单关闭和我的订单。
- Mock 收银台和 Mock 支付网关。
- 支付确认、订单履约和权益生成。
- 管理员订单查询、主动查单、补履约、关单和全额退款。
- 超时关单、支付补偿、退款补偿和异常订单视图。
- 审计、指标、告警和故障注入测试。
- 学员端与管理员端的完整浏览器验收。

### 3.3 本阶段不做

- 真实支付宝请求、真实收款和真实退款。
- 充值、余额、积分和混合支付。
- 优惠券、折扣、分销、购物车和多商品订单。
- 家长账号、购买人与学员分离。
- 用户自助退款和自动退款审批。
- 发票、合同和财务税务功能。
- 智能客服机器人。仅在订单查询接口中保留后续接入所需的状态与时间线数据。

## 4. 数据模型

### 4.1 系列销售信息

在现有 `t_series` 上增加：

- `sale_status`：`OFF_SALE / ON_SALE`
- `price_cents`：`BIGINT`，人民币分
- `currency`：默认 `CNY`
- `sale_updated_at`

规则：

- 只有管理员可以修改价格和销售状态。
- `DRAFT` 或 `ARCHIVED` 系列不能开启销售。
- 已发布但未开启销售的系列只对已有权益学员可见，不出现在课程商店。
- 价格变更只影响新订单，历史订单使用订单内价格快照。

### 4.2 权益模型

扩展现有 `t_student_series`，将其语义升级为权益记录：

- `source`：`ADMIN / ORDER`
- `status`：`ACTIVE / REVOKED`
- `order_id`：订单来源时必填
- `valid_from`
- `valid_to`：本阶段为空，表示长期有效
- `granted_by`
- `granted_at`
- `revoked_at`
- `revoke_reason`

约束：

- 同一学员、同一系列、同一来源最多一条权益。
- 现有 `t_student_series` 记录迁移为 `source=ADMIN`、`status=ACTIVE`。
- 管理员分配只修改 `ADMIN` 来源，不删除 `ORDER` 来源。
- 学员访问课程时判断是否存在 `ACTIVE` 权益，不再要求系列当前必须是 `PUBLISHED`。
- 管理员显式撤销订单权益必须填写原因并进入审计。

### 4.3 订单

新增 `t_order`：

- `id`
- `order_no`：业务订单号，唯一
- `user_id`
- `series_id`
- `series_title_snapshot`
- `price_cents`
- `currency`
- `status`
- `pay_channel`
- `provider_trade_no`
- `client_request_id`
- `active_key`：非终态订单的用户+系列唯一键，终态置空
- `expire_at`
- `paid_at`
- `fulfilled_at`
- `closed_at`
- `created_at`
- `updated_at`
- `version`

索引与约束：

- `UNIQUE(order_no)`
- `UNIQUE(user_id, client_request_id)`
- `UNIQUE(active_key)`，利用 MySQL 多 NULL 规则保证同一学员同一系列最多一个进行中订单
- `KEY(user_id, created_at)`
- `KEY(status, expire_at)`

不单独建订单明细表。本阶段固定一个订单对应一个系列；后续引入多商品购物车时再迁移到订单明细模型。

### 4.4 支付尝试

新增 `t_payment_attempt`：

- `id`
- `order_id`
- `attempt_no`
- `channel`
- `status`
- `provider_trade_no`
- `request_digest`
- `failure_code`
- `failure_message`
- `created_at`
- `finished_at`

同一订单可以有多条支付尝试，但只允许一个成功支付结果决定最终权益。

### 4.5 支付事件

新增 `t_payment_event`：

- `id`
- `provider`
- `event_key`：真实支付时为通知唯一标识；Mock 也必须生成稳定事件键
- `order_no`
- `event_type`
- `payload_digest`
- `process_status`：`RECEIVED / PROCESSED / FAILED`
- `error_message`
- `received_at`
- `processed_at`

约束：

- `UNIQUE(provider, event_key)`
- 原始敏感字段不入库，只保存必要交易字段、脱敏摘要和处理结果。
- 重复事件不能重复生成权益。

### 4.6 退款

新增 `t_refund`：

- `id`
- `refund_no`：唯一
- `order_id`
- `amount_cents`
- `status`：`CREATED / PROCESSING / SUCCEEDED / FAILED`
- `provider_refund_no`
- `reason`
- `requested_by`
- `requested_at`
- `succeeded_at`

本阶段只支持全额退款。退款成功后才撤销 `ORDER` 来源权益；`ADMIN` 权益不受影响。

## 5. 订单状态机

建议状态：

```text
CREATED
  -> PAYING
  -> PAID
  -> FULFILLED

CREATED / PAYING -> PAY_FAILED
PAY_FAILED -> PAYING

CREATED / PAYING / PAY_FAILED -> CLOSED

PAID / FULFILLED -> REFUNDING -> REFUNDED
PAID / FULFILLED -> REFUND_FAILED
REFUND_FAILED -> REFUNDING
```

规则：

- 只有 `CREATED` 可以发起首次支付。
- `PAY_FAILED` 在订单未过期前允许重试。
- 支付成功后先落 `PAID`，再执行履约；两者分开，避免“钱已到账但权益生成失败”不可见。
- `PAID` 不再是用户可操作状态，后台补偿任务负责推进到 `FULFILLED`。
- `FULFILLED` 表示权益已经生成或已存在。
- 已关闭订单不能支付。
- 已支付订单不能直接关闭，只能退款。
- 任何状态变更都必须带旧状态条件，禁止无条件覆盖。

## 6. 支付网关抽象

定义窄接口，只包含当前和未来真实支付都需要的动作：

```text
PaymentGateway
  initiate(order, attempt) -> PaymentAction
  query(order) -> PaymentQueryResult
  refund(refund) -> RefundResult
  parseNotification(request) -> PaymentNotification
```

实现：

- `MockPaymentGateway`：本阶段唯一启用的支付实现。
- `AlipayPaymentGateway`：后续真实接入时实现，本阶段不写网络调用。
- 支付确认、订单状态更新和权益履约占同一个业务服务，供 Mock 通知、真实通知和主动查单共同调用。

配置约束：

- `app.payment.provider=mock` 只允许在 `local/dev/test` 环境启用。
- `prod` profile 启动时如果仍是 mock，必须直接失败。
- 非 Mock 环境不得暴露模拟支付入口。
- 支付回调路径不依赖登录 JWT，但必须经过 Mock 签名或未来真实支付宝验签。

## 7. Mock 收银台设计

Mock 收银台不是直接改订单状态，而是模拟支付平台：

```text
学员订单
  -> 后端创建 PaymentAttempt
  -> 返回 Mock 收银台地址
  -> 学员在 Mock 收银台选择场景
  -> Mock 网关产生“支付结果事件”
  -> 调用统一支付确认服务
  -> 更新支付尝试和订单
  -> 异步履约并生成权益
```

Mock 收银台只显示：

- 环境标识 `MOCK PAYMENT`
- 订单号
- 课程标题
- 应付金额
- 支付场景按钮
- “返回订单”说明

不允许 Mock 收银台接收自定义金额，也不允许前端直接提交“已支付”状态改数据库。

## 8. 故障注入矩阵

Mock 必须提供以下场景，普通学员端默认只展示“模拟支付成功”和“模拟支付失败”，其余场景由测试接口或开发工具触发：

| 场景 | 触发方式 | 系统预期 |
|---|---|---|
| 正常支付 | 成功事件 | 订单 `PAID -> FULFILLED`，生成一条权益 |
| 支付失败 | 失败事件 | 订单 `PAY_FAILED`，不生成权益，可重试 |
| 主动取消 | 关闭 Mock 收银台并取消订单 | 订单 `CLOSED`，不生成权益 |
| 用户关闭页面 | 不发送前端通知 | 订单保留，用户可从订单页继续查询 |
| 回调丢失 | 模拟平台已扣款但不回调 | 主动查单补偿后完成履约 |
| 回调延迟 | 延迟 5～30 秒发送事件 | 订单保持 `PAYING`，最终完成 |
| 重复回调 | 同一事件发送 2 次以上 | 只记录一次，只生成一条权益 |
| 金额不一致 | 事件金额与订单金额不同 | 拒绝履约，记录异常并告警 |
| 未知订单 | 通知使用不存在的订单号 | 拒绝处理并记录告警 |
| 履约失败 | 模拟权益写入失败 | 订单停在 `PAID`，补偿任务重试 |
| 服务重启 | 履约前重启后端 | 重启后补偿任务继续推进 |
| 退款成功 | 管理员发起全额退款 | 订单 `REFUNDED`，撤销订单来源权益 |
| 退款失败 | 模拟网关失败 | 订单 `REFUND_FAILED`，权益保持，可重试 |
| 重复退款 | 对同一订单再次发起 | 返回已有退款，不重复扣款 |
| Redis 不可用 | 停止 Redis | 订单与幂等仍由 MySQL 保证，接口降级但不重复履约 |

## 9. 支付确认与履约

### 9.1 支付确认事务

1. 校验 Mock 签名或未来支付宝验签。
2. 校验 `order_no`、`app_id`、金额和交易状态。
3. 用 `UNIQUE(provider, event_key)` 防止重复事件。
4. 支付尝试从 `PROCESSING` 更新为 `SUCCESS`。
5. 订单从 `PAYING` 条件更新为 `PAID`，写入支付时间和平台交易号。
6. 提交事务，保留“钱已确认”的证据。

### 9.2 履约事务

1. 只处理 `PAID` 且未 `FULFILLED` 的订单。
2. 按订单创建 `ORDER` 来源权益。
3. 权益使用唯一约束和幂等写入，不产生重复记录。
4. 订单更新为 `FULFILLED`。
5. 如果失败，订单保持 `PAID`，记录失败原因并进入补偿队列。

### 9.3 主动查单

定时扫描：

- 超过 1 分钟仍为 `PAYING` 的订单。
- `PAID` 超过 30 秒未履约的订单。
- `REFUNDING` 长时间未完成的退款。

Mock 查单返回预置场景结果；真实支付接入后改为调用支付宝查单接口。主动查单与回调共用同一套确认和履约服务。

## 10. 用户交互

### 10.1 课程商店

- 只展示 `PUBLISHED + ON_SALE + price_cents > 0` 的系列。
- 卡片显示价格、教师、章节数、视频数和购买状态。
- 已拥有权益显示“已拥有”，不显示购买按钮。
- 存在进行中订单显示“继续支付”。
- 支付失败显示“重新支付”。
- 已关闭或过期显示“重新购买”。

### 10.2 确认与支付

- 购买前显示订单确认页：课程、价格、有效期、退款说明。
- 金额全部来自后端订单，前端只展示。
- 发起支付时按钮进入 loading，禁止重复点击。
- 支付等待页显示订单号、金额、创建时间和剩余支付时间。
- 默认每 3 秒轮询一次，逐步退避，最长 2 分钟后停止自动轮询并提供“手动查询”。
- 不把支付成功页的 URL 当作支付成功依据，必须以后端订单状态为准。
- 成功页提供“开始学习”和“查看订单”。
- 失败、过期和网络异常都提供明确下一步，不让用户停在空白页。

### 10.3 我的订单

- 展示订单号、课程、金额、状态、创建时间和操作。
- 支持继续支付、查看详情、重新查询。
- 管理员退款后显示退款结果和权益状态。
- 不提供用户自助退款。

## 11. 管理端

- 系列销售设置：价格、开启/关闭销售。
- 订单列表：按订单号、学员、系列、状态和时间筛选。
- 订单详情：状态时间线、支付尝试、支付事件、权益生成和退款记录。
- 主动查单。
- 手动补履约，必须幂等并记录审计。
- 关闭未支付订单。
- 全额退款。
- 异常订单筛选：支付成功未履约、金额不一致、回调失败、退款失败、长时间待支付。
- 管理员不允许直接修改订单金额或伪造支付成功。

## 12. 建议接口

学员：

- `GET /api/catalog/series`：课程商店。
- `GET /api/catalog/series/{id}`：可售系列详情和购买状态。
- `POST /api/orders`：创建或复用进行中订单。
- `POST /api/orders/{orderNo}/pay`：发起支付，返回 Mock 收银台地址或未来支付宝支付动作。
- `GET /api/orders/{orderNo}`：订单状态和时间线。
- `GET /api/orders`：我的订单。
- `POST /api/orders/{orderNo}/close`：关闭未支付订单。

管理员：

- `PUT /api/admin/series/{id}/sale`：设置价格和销售状态。
- `GET /api/admin/orders`：订单分页。
- `GET /api/admin/orders/{orderNo}`：订单详情。
- `POST /api/admin/orders/{orderNo}/query`：主动查单。
- `POST /api/admin/orders/{orderNo}/fulfill`：补履约。
- `POST /api/admin/orders/{orderNo}/close`：关闭未支付订单。
- `POST /api/admin/orders/{orderNo}/refund`：发起全额退款。
- `GET /api/admin/orders/exceptions`：异常订单。

Mock：

- `GET /api/payments/mock/{orderNo}`：Mock 收银台数据。
- `POST /api/payments/mock/{orderNo}/events`：生成 Mock 支付事件。
- 仅在 Mock 环境开放，非 Mock 环境返回 404。

所有写接口继续使用：

- `@RequireRole`
- `@RateLimit`
- `@AdminAudit`
- `@Measured`
- 请求 `traceId`

订单级幂等不能只依赖 Redis `@Idempotent`，必须叠加数据库唯一键和条件更新。

## 13. 安全要求

- 后端根据 `series_id` 读取价格，不接受客户端金额。
- 订单只允许购买自己的账号，不能替其他学员下单。
- 支付事件必须校验订单号、金额、渠道和环境。
- Mock 事件也必须带签名、时间戳和 nonce。
- 敏感密钥不进入仓库，不在日志中打印完整通知体。
- 支付回调请求体设置大小上限，并记录摘要而不是完整敏感内容。
- 关闭订单和退款接口必须校验当前状态，防止重复退款。
- 管理员补履约和撤销权益必须进入管理员审计。
- 订单创建、支付发起和回调入口增加限流。
- Mock 配置在 `prod` profile 下必须启动失败，不能靠人工记得关闭。

## 14. 可观测性

日志字段：

- `orderNo`
- `paymentAttemptNo`
- `eventKey`
- `provider`
- `fromStatus → toStatus`
- `traceId`
- 失败原因和异常类型

指标：

- `payment.order.created`
- `payment.order.paid`
- `payment.order.fulfilled`
- `payment.order.closed`
- `payment.callback.received`
- `payment.callback.invalid`
- `payment.callback.duplicate`
- `payment.fulfillment.failed`
- `payment.pending.count`
- `payment.refund.failed`
- `payment.reconcile.mismatch`

告警：

- 支付成功超过 1 分钟未履约。
- 金额不一致或未知订单通知。
- 退款失败。
- 支付回调验签失败连续出现。
- 待支付订单长时间堆积。
- 补偿任务连续失败。

## 15. 测试计划

### 单元测试

- 订单状态机合法和非法迁移。
- 金额校验。
- 权益幂等生成和撤销。
- 退款金额与状态校验。
- 价格快照。
- 重复订单和重复支付请求。

### 集成测试

- 创建订单后 Redis 停止，仍可继续支付和履约。
- 同一 `clientRequestId` 并发创建只得到一个订单。
- 回调重复、乱序、金额错误。
- 回调丢失后主动查单补偿。
- `PAID` 后履约失败，补偿任务恢复。
- 服务重启后补偿继续。
- 管理员退款成功、失败、重复退款。
- 管理员分配与订单权益互不覆盖。

### 浏览器测试

- 学员购买成功并进入学习空间。
- 支付等待、刷新页面、关闭页面后重新查询。
- 支付失败、过期、重新支付。
- 管理员设置价格和销售状态。
- 管理员查单、补履约、退款。
- 390px 窄屏完整购买链路。

### 故障演练

按故障注入矩阵逐项执行，结果写入验收记录。不能只测试“支付成功”路径。

## 16. 实施批次

### 批次 A：业务规则与数据迁移

- 冻结本方案默认规则。
- 系列销售字段。
- 权益字段和旧数据迁移。
- 订单、支付尝试、支付事件、退款表。
- 状态机和唯一约束测试。

### 批次 B：销售与权益

- 管理员定价和销售开关。
- 课程商店。
- 管理员分配只管理 `ADMIN` 权益。
- 学员访问改为按权益判断。
- 已购课程状态变化不丢访问。

### 批次 C：订单与 Mock 支付

- 创建、复用、关闭订单。
- 支付尝试和 Mock 收银台。
- Mock 支付事件签名。
- 统一支付确认服务。
- 权益履约。
- 订单查询和我的订单。

### 批次 D：管理员订单运营

- 订单列表、详情和时间线。
- 主动查单。
- 补履约。
- 全额退款。
- 异常订单视图。

### 批次 E：可靠性收口

- 超时关单任务。
- 支付补偿和退款补偿任务。
- 指标、告警和审计。
- 故障注入矩阵。
- 并发和重启演练。
- 浏览器 E2E。

### 批次 F：真实支付宝接入（后续）

- 支付宝沙箱适配器。
- 验签、查单、退款和沙箱验收。
- 真实商户资质和 HTTPS 回调准备。
- 不改变订单、权益和用户交互模型。

## 17. 验收标准

- 管理员可以给已发布系列设置价格并开启销售。
- 学员可以下单、完成 Mock 支付并获得权益。
- 同一学员同一系列不会产生重复进行中订单。
- 重复回调、重复点击、并发请求只生成一条有效权益。
- 回调丢失可由主动查单补偿。
- `PAID` 后服务异常不会永久丢失履约，补偿任务可恢复。
- 退款成功只撤销订单来源权益，不影响管理员分配。
- 订单价格使用快照，后续改价不影响历史订单。
- Redis 不可用时，支付正确性不被破坏。
- Mock 入口在非 Mock 环境不可用。
- 管理员可以看到异常订单并执行补偿操作。
- 桌面和窄屏购买链路、订单页、退款页通过浏览器验收。
- 测试订单、权益、支付事件和退款数据在验收后清理。

## 18. 智能客服预留

智能客服不计入本阶段。

订单详情接口会保留可复用的状态时间线：

- 订单创建
- 支付发起
- 支付成功/失败
- 履约完成
- 退款发起/成功/失败

后续客服只需要读取订单状态和时间线，不应直接修改支付或权益数据。
