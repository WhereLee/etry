# 生产回滚演练（2026-11-24）

## 目标

验证后端镜像与前端 webroot 都能在故障时快速回退，并在演练后自动恢复当前版本。

## 后端

- 当前镜像保存为 `btry-backend:drill-current`。
- 选择最近的历史镜像 `btry-backend:prev-20261124-155036` 作为回滚版本。
- `docker tag prev btry-backend:latest` 后 `compose up -d backend`，readiness 通过。
- 行为差异验证：当前版本登录响应包含 `user.roles`；回滚版本不包含。
- 回滚期间 `/api/health` 返回 200，RabbitMQ 队列消费者保持在线。

## 前端

- 当前 webroot：`/var/www/whereri.site/learn`。
- 历史 webroot：`learn.prev-20261124-155036`。
- 交换后入口资源从 `assets/index-BiqpdZVZ.js` 变为 `assets/index-DYbjyQp3.js`，说明确实切换到旧构建。
- 回滚期间 `/learn/` 返回 200。

## 恢复

- 脚本 trap 自动恢复镜像 tag、重建 backend、等待 readiness。
- webroot 自动换回当前目录，临时目录删除。
- 最终验证：

```
final_login_roles=yes
assets/index-BiqpdZVZ.js
btry.outbox.fulfill consumers=1
btry.outbox.revoke consumers=1
btry.outbox.dlq consumers=1
web=200 api=200
```

## 结论

- 后端回滚与前端原子切换均可在分钟级完成，回滚期间站点可用。
- 回滚脚本 `tools/rollback-drill.sh` 可重复执行，异常时自动恢复。
- 镜像保留策略与 webroot 保留数量足以支撑一次快速回退。
