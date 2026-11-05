# JVM 与 GC 基线报告

更新日期：2026-11-05

## 1. 环境

- staging backend 容器内存上限：512 MiB
- `-XX:MaxRAMPercentage=70` -> 最大堆约 360 MiB
- Java：Temurin 17.0.21
- GC：G1
- 其他参数：`-XX:+ExitOnOutOfMemoryError`、`-XX:+HeapDumpOnOutOfMemoryError`、GC 日志
- 生产 systemd 仍为独立 JVM，参数未改

## 2. 参数快照

压测中 `jcmd 1 VM.flags` 的关键值：

```text
-XX:MaxHeapSize=377487360
-XX:MaxRAM=536870912
-XX:MaxRAMPercentage=70.000000
-XX:+UseG1GC
-XX:+ExitOnOutOfMemoryError
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs
```

## 3. 空闲与负载数据

压测进行中快照：

```text
garbage-first heap   total 140288K, used 101332K
Metaspace            used 92821K
```

压测刚开始时的 `jstat`：

```text
YGC 113, YGCT 0.621s
FGC 0,   FGCT 0.000s
CGC 46,  CGCT 0.187s
GCT 0.808s
```

压测结束时：

```text
YGC 281, YGCT 1.023s
FGC 0,   FGCT 0.000s
CGC 74,  CGCT 0.299s
GCT 1.322s
```

压测结束后堆：

```text
garbage-first heap   total 140288K, used 93163K
```

Prometheus 5 分钟窗口：

- `max_over_time(hikaricp_connections_active[5m])` = 3
- `max_over_time(hikaricp_connections_pending[5m])` = 0
- `max_over_time(jvm_gc_pause_seconds_max[5m])` = 0.01s

容器资源：

- backend：约 382 MiB / 512 MiB
- mysql：约 394 MiB / 512 MiB
- web：约 4.5 MiB / 48 MiB
- redis：约 4.2 MiB / 128 MiB

## 4. 结论

- 没有 Full GC，没有 OOM，最大单次 GC 暂停约 10ms；
- 堆在负载结束后回落到 93 MiB，未看到持续上涨；
- Hikari 峰值活跃连接只有 3，等待为 0，当前 10 连接配置足够；
- 没有数据支持立即修改 G1 参数或扩大连接池；
- 默认 G1 + `MaxRAMPercentage=70` 保留，后续只有在 200 VU 以上压测或长视频场景出现证据时才调整。

## 5. 使用方式

```bash
# 进入容器
sudo docker exec -it btry-backend-1 sh

# 查看堆
jcmd 1 GC.heap_info

# 查看 GC
jstat -gcutil 1 1000 30

# 采集 JFR（测试窗口）
jcmd 1 JFR.start name=btry-load duration=120s filename=/app/logs/load.jfr
```

GC 日志位于容器卷 `/app/logs/gc.log`，已配置 5 个文件、单文件 20MB 轮转。

## 6. 注意

- JDK 运行时比 JRE 镜像大约多 100MB，但换来了 `jcmd/jstat/jstack`，staging 诊断价值更高；生产 systemd 不受影响。
- `HeapDumpOnOutOfMemoryError` 只用于 staging，避免生产磁盘被意外占满。
- 压测与 observability 同时在 3.7GB 主机上运行，数据用于定位瓶颈，不作为绝对容量承诺。
