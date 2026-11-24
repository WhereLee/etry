# P2 RabbitMQ 生产接入与端到端演练（2026-11-24）

## 1. 部署拓扑

- 镜像：`rabbitmq:3.13-management`，容器 `btry-rabbitmq-1`，内存上限 512m。
- 管理台仅监听宿主机回环：`127.0.0.1:15672`；AMQP 仅容器网络内暴露。
- 健康检查：`rabbitmq-diagnostics -q ping`（10s 间隔）。
- 账号/口令来自 `/opt/etry/.env.docker`（600），未写入仓库。

### 启动故障与修复

首次启动容器反复 `Restarting (1)`，日志：

```
error: RABBITMQ_VM_MEMORY_HIGH_WATERMARK is set but deprecated
error: deprecated environment variables detected
```

根因：RabbitMQ 3.13 将已废弃的环境变量视为致命错误并退出。
修复：删除 `RABBITMQ_VM_MEMORY_HIGH_WATERMARK`，内存水位使用默认值；
容器 20 秒内转为 healthy。同时将 `RABBITMQ_MEM_LIMIT` 调至 `512m`。
故障期间 backend 因 `depends_on: rabbitmq: healthy` 未启动（API 502），
修复后随依赖健康自动恢复，`/api/health` 返回 200。

## 2. 生产拓扑与消费者

```
TopicExchange btry.outbox
  ├─ routing fulfill → Queue btry.outbox.fulfill (DLX: btry.outbox.dlx/dead)
  └─ routing revoke  → Queue btry.outbox.revoke  (DLX: btry.outbox.dlx/dead)
DirectExchange btry.outbox.dlx
  └─ routing dead     → Queue btry.outbox.dlq
```

实测 `rabbitmqctl list_queues`：

```
name                messages consumers
btry.outbox.fulfill 0        1
btry.outbox.dlq     0        1
btry.outbox.revoke  0        1
```

绑定关系经 `list_bindings` 核对，与代码常量一致。
消费者配置：`default-requeue-rejected=false`，重试 3 次（1s 起步，2 倍退避，上限 10s），
耗尽后经 DLX 进入死信队列，死信消费者写 `t_alert_log`。

## 3. 代码修正

演练前发现：`OutboxRabbitConsumer` 忽略 `fulfill/revoke` 的 `false` 返回值，
消费端失败会被 ACK，无法触发重试与死信。已修正为失败抛异常：

```java
if (!fulfillmentService.fulfill(event.orderNo())) {
    throw new IllegalStateException("履约未完成 orderNo=" + event.orderNo());
}
```

新增 `OutboxRabbitConsumerTest` 覆盖成功、失败、死信告警 4 个用例。
服务器容器内单测：`Tests run: 245, Failures: 0, Errors: 0`。

## 4. 端到端演练（生产容器 + 生产 RabbitMQ）

受控写入两条测试订单与对应发件箱记录（演练后已删除）：

| 订单 | 初始状态 | 发件箱 | 预期 |
|---|---|---|---|
| MQE2E-20261124-OK | PAID | FULFILL | 履约成功 |
| MQE2E-20261124-FAIL | CREATED | FULFILL | 消费失败→重试→DLQ→告警 |

### 4.1 成功履约（outbox→MQ→consumer）

- 发件箱由 relay 认领并投递，`t_outbox_message`：`FULFILL / DONE`。
- 订单：`PAID → FULFILLED`，`fulfilled_at=2026-11-24 01:18:44.014`。
- 权益：`t_entitlement id=29 user=3 product=1 COURSE_SERIES ACTIVE`。
- 后端日志：

```
order_fulfilled orderNo=MQE2E-20261124-OK userId=3 seriesId=1
```

### 4.2 撤销（outbox→MQ→consumer）

为同一订单追加 `REVOKE` 发件箱事件：

- 发件箱：`REVOKE / DONE`。
- 权益：`ACTIVE → REVOKED`，`revoke_reason=MQ E2E revoke drill`。
- 日志：`order_entitlement_revoked orderNo=MQE2E-20261124-OK`。

### 4.3 失败重试与死信

- 订单保持 `CREATED`（不可履约）。
- 消费者每次抛出 `IllegalStateException: 履约未完成`，日志出现两次重试堆栈。
- 重试耗尽后消息进入 `btry.outbox.dlq`，死信消费者处理并落库：

```
t_alert_log id=1 alert_type=OUTBOX_MQ_DEAD severity=ERROR
message=outbox MQ 死信 orderNo=MQE2E-20261124-FAIL type=FULFILL outboxId=2
delivered=0
```

`delivered=0`：业务告警未配置外部 webhook，按现状落库；
Prometheus→Alertmanager→alert-sink 的运维告警链路此前已独立演练（见 observability.md）。

- 演练结束后三个队列均为 `0 messages`，消费者保持在线。
- 测试订单、权益、发件箱记录已全部清理（leftover=0）。

## 5. 结论

- 生产 RabbitMQ 已健康运行，队列、绑定、消费者、DLX 全部就绪。
- 发件箱 `fulfill`、`revoke` 两类消息均完成真实投递与消费。
- 消费失败可重试、可死信、可告警，不再静默 ACK。
- 管理台未对公网暴露；故障根因与处置已固化为可复现的运维记录。
