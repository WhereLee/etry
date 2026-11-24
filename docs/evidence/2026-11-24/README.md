# 2026-11-24 证据索引

| 主题 | 文件 |
|---|---|
| 试读缓存与容量 | `p1-capacity-preview-cache.md` |
| 交易内核（pay/outbox/租约结论） | `p1-kernel.md` |
| RabbitMQ 生产接入与 DLQ | `p2-rabbitmq.md` |
| Redis 降级与恢复 | `p2-redis.md` |
| RocketMQ staging 顺序/延迟演练 | `p2-rocketmq.md` |
| 依赖升级收口与 OSV 复扫 | `security-rescan.md` |
| 部署后运行时验收 | `postdeploy-verification.txt` |
| OSV 当前依赖原始结果 | `backend-osv-bc85.json`、`backend-osv-bc85-details.json` |
| 前端 RBAC 与 E2E | `p3-rbac-e2e.md` |
| 生产回滚演练 | `p3-rollback-drill.md` |
| E2E 截图 | `e2e/guest-materials.png`、`e2e/admin-console.png`、`e2e/student-redirect.png` |
| P2/P3 问题日志 | `development-log-p2p3.md`（已合并到 docs/development-log.md） |

## 2026-11-24 依赖收口原始日志

以下日志保留在服务器 `/opt/etry`，因 `.gitignore` 忽略 `*.log` 未提交；摘要与结论已固化在
`security-rescan.md` 和 `postdeploy-verification.txt`。

- `test-logs/all-bc85-20261124-222829.log`：245/245 单测 + 101/101 集成。
- `deploy-logs/deploy-bc85-20261124-223305.log`：新镜像构建、前端切换、readiness UP。
- `deploy-logs/deploy-20261124-221510.log`：首次部署（发现 COS 传递 BC 1.70 的那一版，已保留作问题追溯）。
| 依赖清零复扫 | `security-zero.md` |
