## 2026-11-24 P2/P3（RabbitMQ/Redis/RBAC/E2E/回滚）

### 问题: RabbitMQ 3.13 因废弃环境变量直接退出
- 现象: 容器 Restarting (1)，日志 `RABBITMQ_VM_MEMORY_HIGH_WATERMARK is set but deprecated`
- 解决: 移除该环境变量，内存上限改 512m；容器恢复 healthy，backend 自动启动
- 预防: MQ 镜像升级前核对 deprecated env；健康检查纳入 compose 依赖

### 问题: Rabbit 消费者吞掉履约/撤销失败，无法重试和进死信
- 现象: `fulfillmentService.fulfill()` 返回 false 时消费者仍 ACK
- 解决: 返回 false 抛 IllegalStateException；新增 4 个消费者单测；生产实测重试 3 次后进 DLQ 并写 `t_alert_log`
- 预防: 消费者必须区分“成功处理”和“业务未完成”，后者不能 ACK

### 问题: Playwright 安装依赖拉取大量字体导致超时
- 现象: `install --with-deps` 10 分钟仍在下载 fonts-noto-color-emoji 等
- 解决: Debian 源切阿里云，仅安装 Chromium 运行必需库；浏览器用 npmmirror 镜像
- 预防: E2E 运行镜像只装 headless 必需依赖，不装完整字体集

### 问题: 外部 API 路径误用 /learn/api
- 现象: 登录 POST 返回 405
- 原因: `/learn/` 是前端静态，API 映射在 `/api/`
- 解决: E2E/压测统一使用 `/api`
- 预防: 文档明确 nginx 路由边界，避免把前端 base 当 API base

### 问题: Playwright 登录按钮 name 不匹配
- 现象: `getByRole('button', { name: '登录' })` 超时
- 原因: 按钮实际文案为“进入学习空间”
- 解决: 选择器改为实际文案；3 个 E2E 用例通过
- 预防: E2E 选择器以可访问名称为准，先读模板再写断言

### 回滚演练
- 后端历史镜像回滚后登录响应不含 roles，readiness/API 200；trap 自动恢复
- 前端 webroot 切换后入口资源 hash 改变，/learn/ 200；自动恢复当前构建
- 最终 roles 恢复、队列消费者 3/3、web/api 200
