# RocketMQ Staging 演练（2026-11-24）

## 定位

生产支付履约仍使用 RabbitMQ + 事务性发件箱。本演练只在服务器上以低配容器验证
RocketMQ 的顺序消息、延迟消息与消费位点，不接入生产业务，演练后容器和网络全部销毁。

## 拓扑与资源

- 镜像：`apache/rocketmq:5.3.1`
- NameServer：`JAVA_OPT_EXT=-Xms256m -Xmx256m -Xmn128m`，仅 `127.0.0.1:9876`
- Broker：`JAVA_OPT_EXT=-Xms512m -Xmx512m -Xmn256m`，`127.0.0.1:10911`
- `broker.conf`：ASYNC_MASTER、ASYNC_FLUSH、自动建 Topic/订阅组，`brokerIP1=127.0.0.1`
- 实测内存：NameServer 188MiB，Broker 770MiB；演练结束即停止，未影响生产。

## 客户端脚本

`tools/rocketmq-drill/RocketMqDrill.java`：

- 独立 topic（带时间戳）与 consumer group，避免历史位点干扰。
- 20 条顺序消息全部走同一队列，`MessageListenerOrderly` 消费。
- 1 条 `delayLevel=2` 延迟消息。
- 结果写入 `target/drill-result.txt` 并校验后退出。

## 实测结果

```
topic=btry-drill-topic-1795510939918
ordered_received=20
ordered_out_of_order=0
delayed_elapsed_ms=21317
completed=true
passed=true
```

Broker 位点：

```
broker-a  QID 0  min=0  max=20
broker-a  QID 1  min=0  max=1
consumerProgress: Consume Diff Total=0, Inflight=0, Consume TPS=2.10
```

结论：

- 顺序消息在单队列上无乱序（20/20）。
- 延迟消息被延迟投递（实测约 21s，晚于普通消息）。
- 消费位点追平，无堆积，说明客户端与 Broker 交互正常。
- 资源占用约 1GiB，不适合与生产 RabbitMQ/MySQL/Redis 长期共存于 3.7G 单机；
  生产继续 RabbitMQ，RocketMQ 作为面试/架构对比与 staging 经验保留。

## 复现

```bash
# 启动低配 namesrv/broker（见 /opt/etry/perf/rocketmq/broker.conf）
# 运行
NAMESRV_ADDR=127.0.0.1:9876 \
  mvn -s server/docker/maven-settings.xml compile exec:java
```
