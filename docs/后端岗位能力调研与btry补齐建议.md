# 后端岗位能力调研与 btry 补齐建议

更新日期：2026-11-05

## 1. 调研范围与方法

本次调研结合了三类信息：

1. **在线招聘 JD 样本**：通过腾讯招聘公开接口检索 Java/后端/JVM/分布式/高并发关键词，抓取当前岗位标题和职责描述，统计关键词出现频次。
2. **技术官方文档**：JVM/GC、Spring Boot、MySQL、Redis、RocketMQ、Kubernetes、OpenTelemetry。
3. **开发者生态调查**：Stack Overflow Developer Survey 2024，作为技术采用度的辅助参考。

需要说明：JD 是招聘方的高度概括，不能代表面试深度。JVM/GC、并发、数据库原理等经常出现在面试中，但不一定写在 JD 里，因此不能只用关键词频率决定学习内容。

## 2. 招聘 JD 观察

本次抓取了 100 条 Java 关键词岗位，过滤掉测试、前端、客户端等非后端岗位后，得到约 42 条后端/服务端相关岗位。对岗位标题和职责描述做关键词统计，结果如下：

| 能力/关键词 | 出现岗位数（约） | 说明 |
|---|---:|---|
| 性能 | 34/42 | 最高频，通常与复杂链路、SQL、JVM、缓存和压测一起出现 |
| 稳定性 | 22/42 | 线上问题治理、故障排查、可用性保障 |
| 链路/监控 | 11/5 | 复杂调用链、监控、告警和排障能力 |
| 并发/高并发 | 8/7 | 交易、营销、消息、流量场景 |
| 高可用 | 7/42 | 容错、降级、限流、故障恢复 |
| 分布式 | 6/42 | 分布式系统设计和问题定位 |
| 数据库/SQL | 1/3 | JD 常写得较概括，但面试和实际工作权重很高 |
| 缓存 | 3/42 | Redis、热点数据、缓存一致性 |
| JVM/GC | 1/1 | JD 明示频率不高，但属于 Java 后端基础面试高频区 |
| 消息队列/Kafka | 1/1 | 常见于交易、营销、异步任务岗位 |
| K8s/Docker | 0~2/42 | 平台/基础架构岗位更常见 |
| 设计模式/DDD | 0/42 | JD 很少直接写，但会在系统设计和代码评审中考察 |
| 算法 | 6/42 | 部分岗位明确要求，校招/大厂面试更常见 |

补充检索结果：

- 以“JVM”检索的岗位，重点仍然集中在**并发、性能、高并发、稳定性、SQL、消息队列**，而不是单独考 GC 名词。
- 以“高并发”检索的岗位中，**性能、稳定性、高可用、监控、链路、分布式、缓存**明显增多，GC 也会作为性能治理的一部分出现。
- 以“分布式”检索的岗位更强调**性能、链路、稳定性、云原生、数据库、缓存和可观测性**。

腾讯的一条典型高级 Java 岗位职责包括：核心系统研发、海量数据实时处理、高并发架构、稳定性与性能、JVM 调优、SQL 优化和复杂链路故障排查。参见 [腾讯招聘岗位示例](https://careers.tencent.com/jobdesc.html?postId=2117093808993906688)。

## 3. 招聘方真正看重的能力

### 3.1 业务交付与线上治理

JD 中最高频的不是“会背 GC 算法”，而是：

- 能把需求从分析、设计做到上线；
- 能处理性能瓶颈、线上故障和复杂链路；
- 能保证系统稳定、可恢复、可排障；
- 能对 SQL、缓存、消息和资源使用做取舍。

对应到面试，通常会追问：

- 慢 SQL 如何定位，索引为什么失效；
- 接口变慢如何从日志、指标、线程栈、GC 日志定位；
- 缓存穿透、击穿、雪崩和一致性问题；
- 消息重复、丢失、积压和顺序问题；
- 幂等、重试、补偿和最终一致性。

### 3.2 Java 基础与 JVM

JVM/GC 在 JD 中不一定高频，但它是 Java 后端的基础能力，常见面试内容包括：

- 堆、栈、方法区/元空间、直接内存；
- 对象生命周期、可达性分析、常见垃圾收集器；
- G1 的基本思想和停顿时间目标；
- 内存泄漏、OOM、频繁 Full GC 的定位；
- 线程池、锁、CAS、AQS、并发容器；
- JMM、可见性、有序性、原子性。

这里要避免一个误区：**为了简历出现“GC调优”而随意修改生产 JVM 参数，不算工程能力。** 正确做法是先建立基线，再用指标和压测证明改动有效。

### 3.3 数据库与缓存

招聘岗位普遍把 MySQL、Redis 作为基础设施能力，而不是单独列一个“Redis 工程师”：

- MySQL：索引、执行计划、事务隔离、锁、慢查询、连接池；
- Redis：数据结构、过期策略、缓存一致性、热点 key、分布式锁；
- 缓存：本地缓存与分布式缓存的边界，缓存失效和降级。

Stack Overflow 2024 调查中，MySQL 使用率约 40.3%，Redis 约 20%，Spring Boot 约 12.7%。参见 [Stack Overflow 2024 Technology](https://survey.stackoverflow.co/2024/technology)。

### 3.4 消息、分布式与云原生

这些能力常见于中高级岗位，但不应该为了关键词强行引入：

- 消息队列：异步解耦、削峰、重复消费、积压、顺序和死信；
- 分布式：幂等、重试、补偿、分布式锁、事务边界；
- 云原生：Docker、Kubernetes、健康检查、滚动发布、资源限制；
- 可观测性：指标、日志、链路追踪和告警。

RocketMQ、Kubernetes、OpenTelemetry 的官方文档分别覆盖这些能力：

- [RocketMQ Docs](https://rocketmq.apache.org/docs/)
- [Kubernetes Concepts](https://kubernetes.io/docs/concepts/)
- [OpenTelemetry Docs](https://opentelemetry.io/docs/)

### 3.5 工程化与代码质量

招聘方通常不会把下面这些写成完整 JD，但它们会直接影响面试评价：

- Git、CI/CD、代码评审；
- 单元测试、集成测试、接口测试；
- 日志、异常处理、参数校验；
- 配置管理、密钥管理、环境隔离；
- 设计模式、模块边界和可维护性；
- 性能压测和容量评估。

## 4. btry 当前已经具备的能力

btry 的现有代码对后端简历并不弱：

| 能力 | 当前体现 |
|---|---|
| Java/Spring Boot | Java 17、Spring Boot 3.3、REST API、配置绑定 |
| 数据库 | MySQL、Flyway、MyBatis、JdbcTemplate、事务 |
| 缓存 | Redis + Caffeine 本地降级、播放 URL 缓存 |
| 并发与可靠性 | 幂等、限流、条件更新、补偿任务、退款重试 |
| 交易 | 订单、支付事件、履约、权益、退款、异常订单 |
| 外部系统 | COS 预签名、支付宝电脑网站支付、回调验签 |
| 可观测性 | Actuator、Micrometer、Prometheus、traceId、审计和告警 |
| 工程化 | 单元测试、ArchUnit、模块化、CI、部署和备份 |
| 安全 | JWT、BCrypt、token version、CSP、CORS、登录限流 |

这些内容已经可以支撑“单体应用内的交易/内容平台”叙事。不需要为了显得高级而把项目拆成微服务。

## 5. 适合加入 btry 的内容

### P0：最值得做，且和现有项目契合

**1. JVM/GC 基线与可观测性**

不要先改 JVM 参数，先建立基线：

- 使用 JDK 自带 jcmd、jstat、jstack 检查堆、GC、线程；
- 观察 Micrometer 暴露的 JVM 内存、GC 暂停、线程和连接池指标；
- 记录正常流量、支付回调和高并发下单时的 GC 行为；
- 只有在数据证明有停顿或内存问题后，才讨论 G1 参数和堆大小。

价值：把“知道 GC”变成“能定位 GC 相关问题”，这比背名词更有说服力。

**2. 并发与幂等测试**

当前项目已经有订单、支付、退款和补偿，适合补：

- 同一 clientRequestId 并发创建订单；
- 同一订单并发支付；
- 支付回调重复/乱序；
- 退款与履约并发；
- 服务重启后的补偿恢复。

价值：直接对应招聘 JD 里的高并发、稳定性和幂等能力。

**3. SQL 与索引审计**

- 对订单列表、进行中订单、补偿查询和退款重试查询执行 EXPLAIN；
- 记录索引命中、扫描行数和慢查询；
- 形成一份“订单链路 SQL 与索引说明”。

价值：比泛泛写“熟悉 MySQL”更可信。

**4. 支付/退款故障演练**

- 支付成功但履约失败；
- 回调丢失、重复通知、金额不一致；
- 退款超时、退款失败重试耗尽；
- Redis/数据库短暂不可用。

btry 已经有部分故障注入能力，建议把它整理成可重复执行的测试矩阵。

### P1：增强项，适合作品集亮点

**1. Prometheus/Grafana 本地观测面板**

当前已有 Actuator 和 Prometheus 指标，但尚未形成可视化面板。可以增加本地 Docker Compose：

- Prometheus 抓取 Spring Boot 指标；
- Grafana 展示 JVM、HTTP、Hikari、支付和播放指标；
- 配置一组基础告警规则。

注意：这是本地演示/可观测性能力，不建议为了简历增加一套复杂的生产监控平台。

**2. OpenTelemetry 链路追踪**

当前有 traceId 和应用日志，但没有跨 HTTP、数据库和外部支付的完整 span。可以：

- 为支付回调、查单、退款、COS 预签名增加 span；
- 将 traceId 与支付事件、订单、审计日志关联；
- 输出到本地 Jaeger/Tempo。

价值：对应“复杂链路故障排查”和可观测性岗位要求。

**3. Docker 本地运行**

增加：

- 后端 Dockerfile；
- MySQL、Redis、后端和前端的 Compose；
- 健康检查和环境变量模板。

这不会替代当前 systemd 生产部署，但能让面试官一条命令启动项目。

**4. 基础压测**

用 k6 或 wrk 对以下接口做小规模压测：

- 登录；
- 订单创建；
- 订单详情；
- 播放 URL 获取；
- 支付回调模拟。

重点不是追求 QPS 数字，而是记录瓶颈、连接池、GC 和错误率。

### P2：可选，不建议为了关键词硬加

- **MQ**：当前单体、单实例、无跨节点异步消费者，强行加 RocketMQ 只会增加运维成本。等出现通知、报表、异步任务或跨节点消费需求时再加。
- **Kubernetes**：当前是单机 systemd 部署，K8s 对业务价值低。可以作为本地实验，但不要包装成生产架构。
- **Spring Cloud/微服务**：当前规模没有独立部署、独立扩容和团队边界，拆微服务会放大大事务、网络和排障成本。
- **完整 RBAC**：当前三角色 + 资源归属足够；只有出现同角色权限差异或多管理边界时再做。
- **算法刷题**：算法是面试准备，不是业务项目功能，不要把 LeetCode 代码塞进 btry。

## 6. 推荐实施顺序

具体执行步骤、验收标准和排期见 `docs/后端工程深度优化实施计划.md`。

1. JVM/GC 基线与指标观察；
2. 并发/幂等/故障恢复测试；
3. SQL 和索引审计；
4. Prometheus/Grafana 本地面板；
5. OpenTelemetry 或 Docker Compose；
6. 最后再考虑 MQ/outbox，且必须有明确业务触发条件。

## 7. 简历叙事建议

不要写成“使用了很多技术”，而要写成“解决了什么问题”：

- “为订单/支付链路建立幂等、条件更新和补偿机制，处理重复回调、回调丢失和退款失败重试。”
- “通过 Redis URL 缓存、Caffeine 降级和 traceId/指标排查视频播放链路问题。”
- “为课程治理、订单运营和账号管理拆分模块边界，并用 ArchUnit 固化依赖规则。”
- “建立 JVM、连接池、HTTP 和支付指标基线，定位性能瓶颈而不是盲目调参。”

## 8. 结论

招聘市场真正偏好的是：**能交付业务、能保证稳定性、能定位性能和线上问题、理解数据库/缓存/并发/分布式边界，并且有工程化习惯的 Java 后端。**

GC 是 Java 后端的基础能力，但不是唯一指标。对 btry 来说，最合适的补强顺序是：

1. JVM/GC 基线和指标；
2. 并发、幂等和故障恢复测试；
3. SQL/索引审计；
4. 可观测性面板和链路追踪；
5. Docker 本地运行；
6. 有明确需求后再引入 MQ/outbox。

## 9. 参考来源

- [Oracle Java 17 GC Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/)
- [MySQL 8.0 Optimization](https://dev.mysql.com/doc/refman/8.0/en/optimization.html)
- [Redis Documentation](https://redis.io/docs/latest/develop/)
- [Apache RocketMQ Documentation](https://rocketmq.apache.org/docs/)
- [Kubernetes Concepts](https://kubernetes.io/docs/concepts/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Stack Overflow Developer Survey 2024: Technology](https://survey.stackoverflow.co/2024/technology)
- [腾讯招聘 Java 岗位示例](https://careers.tencent.com/jobdesc.html?postId=2117093808993906688)
