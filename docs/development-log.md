# 开发问题日志

## 2026-11-23 阶段0（基线合并）

### 问题: 本机无 Docker，集成测试依赖需本地替代
- 场景: 本地跑 `-Pintegration` 集成测试
- 现象: `docker: command not found`，6379 无监听
- 原因: 本机未安装 Docker；MySQL 8.0.44 原生安装在 3306（root/root），Redis 可执行文件在 `F:\Redis`
- 解决: 直接启动 `F:\Redis\redis-server --port 6379`；手工创建 `btry_it` 库；集成测试用 `IT_DB_URL/IT_DB_USER=root/IT_DB_PASSWORD=root` 环境变量指向本机
- 预防: 本地验证前先探测 3306/6379 端口与已安装服务，不默认依赖 Docker

### 问题: CI actions 运行时弃用告警
- 场景: 推送 main 后 CI 运行 45067251963
- 现象: annotation 提示 checkout@v4 / setup-node@v4 的 Node20 运行时被强制跑在 Node24
- 原因: GitHub Actions 2025-09 起弃用 Node20 运行时
- 解决: 阶段1 CI 加固时升级 checkout@v5 / setup-node@v5
- 预防: 定期看 CI annotations，不止看红绿

## 2026-11-23 阶段1（依赖与 CI 加固）

### 问题: Spring Security starter 默认配置会锁死全部接口
- 场景: 引入 spring-boot-starter-security
- 现象: 风险预判——默认 SecurityFilterChain 对所有请求要求认证，现有自定义 JwtAuthFilter 鉴权会被前置拦截覆盖
- 原因: 项目原鉴权是 servlet 过滤器（JwtAuthFilter），不是 Spring Security 体系
- 解决: 新增 SecurityConfig：无状态 + 关闭 csrf/httpBasic/formLogin + permitAll，认证仍由 JwtAuthFilter 执行；冒烟验证 401/428/登录分支与接入前完全一致。完整 RBAC 在身份阶段落地，届时替换 permitAll
- 预防: 引入安全框架先确认现有鉴权挂载点，行为保持接入后用异常分支冒烟回归

### 问题: ESLint 首跑 2 error + 1387 warning
- 场景: 前端首次接入 ESLint 9 flat config
- 现象: AdminReconciliation.vue 未使用的 ElMessage 导入、AdminUsers.vue 未使用的 toggleSeries 函数；大量 max-attributes-per-line / singleline-html-element-content-newline 格式告警
- 原因: 合并分支遗留死代码；vue flat/recommended 的格式规则与项目既有紧凑模板风格冲突
- 解决: 删除两处死代码（无行为变化）；关闭两条与既有风格冲突的纯格式规则；其余 97 个可自动修复项 `eslint --fix` 处理（App.vue 等仅缩进/属性顺序变化），复验 lint 0 问题、构建通过
- 预防: 新接 lint 先跑一遍看 error/warning 分布，格式类规则按项目既有风格取舍，实质规则保留