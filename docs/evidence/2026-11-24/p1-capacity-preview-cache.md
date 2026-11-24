# P1 容量与试读缓存优化（2026-11-24）

## 修复前（200 VU）
- 100 VU 基线：484 RPS、p95 24.06ms、0% 失败。
- 200 VU 直接复测：**9.33% 失败**、p95 25.24s，后端 **Java heap OOM**（`ExitOnOutOfMemoryError`），容器重启 1 次，`/api` 短暂 502。
- 根因：每次试读都重新加载整本 PDF 并在内存生成截页；200 VU、5% 试读并发导致堆打满。

## 修复
1. `MaterialFileStore.loadOrGeneratePreview`：按「源文件 + 试读页数」磁盘缓存试读 PDF，原子落盘；并发首次生成限 4 个许可（Semaphore）。
2. `MaterialDeliveryService.preview` 改走缓存。
3. backend 限额 800M → **1G**（JVM 堆约 700M）；MySQL 512M → **768M**。
4. 新增 `MaterialFileStoreTest.cachesPreviewOnDiskAndReusesIt`；单测 241/241、资料交付/目录定向集成 15/15。

## 修复后（200 VU，30s）
- 请求：23830，**788.9 RPS**
- p95：**233.82ms**；失败率 **0%**
- 资源：backend 654.4MiB/1GiB（65%）、MySQL 468.1MiB/768MiB（61%）、宿主可用 1.74G
- `/api/health` 200；无 OOM/重启。

## 结论
- 单实例在当前 4C/3.7G 主机上，读路径（目录 80%、搜索 15%、试读 5%）200 VU 可用。
- 200 VU 的 p95 从 25s 降到 234ms 的关键是**试读缓存 + 并发上限**，不是单纯加内存。
- 后续扩容优先：读副本/多实例、CDN 静态资源；写路径（下单/支付）需单独压测。

## 证据
- 修复前日志：`/opt/etry/perf/k6-read-200.log`（OOM）
- 修复后日志：`/opt/etry/perf/k6-read-200b.log`
- 试读缓存文件：`/app/data/materials/demo-math-exam.pdf.preview-3.pdf`
- 代码：`MaterialFileStore.java`、`MaterialDeliveryService.java`、`MaterialFileStoreTest.java`
