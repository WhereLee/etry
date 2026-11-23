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

## 2026-11-23 阶段2（数据模型与契约）

### 问题: 包根重命名漏掉转义/斜杠变体，架构守卫静默失效
- 场景: com.wherelee.edu → com.wherelee.material 全量 sed 替换
- 现象: TableOwnershipTest 的 NAMESPACE 正则（`com\\.wherelee\\.edu`，字面含反斜杠）与 MODULE_PREFIX（`com/wherelee/edu/modules/`，斜杠路径）未被点号模式匹配；守卫扫不到任何模块文件，测试"绿"但实际空转；另一处单测因 mapper namespace 失配报 unknown 模块才暴露
- 原因: 字符串替换只覆盖点号写法，未枚举转义点号、斜杠路径等变体
- 解决: 手工修正两处常量；用 `grep wherelee | grep -v material` 全量复核残留；重跑单测确认守卫真实生效（173 通过）
- 预防: 包名/路径类重命名后，必须按多种书写变体（点号、转义点号、斜杠） grep 复核，且确认架构守卫测试非空转

### 问题: 管道 grep 吞掉 Maven 退出码
- 场景: `mvn test 2>&1 | grep ...` 后台跑测试
- 现象: 任务通知 exit 0，实际单测失败（MojoFailureException）
- 原因: 管道退出码取最后一个命令（grep），掩盖 mvn 失败
- 解决: 改为 `mvn > log 2>&1; echo EXIT=$?` 再 grep 日志文件
- 预防: 验证类命令一律先落盘取真实退出码，再过滤输出

### 问题: 新集成测试放错包触发 ArchUnit 边界规则
- 场景: MaterialStoreCoreIntegrationTest 初建于 modules.material.integration 包
- 现象: 预判——测试类跨模块 import commerce/identity 内部实现，会被 material_should_only_depend_on_platform_and_apis 拦截（ArchUnit 扫描含测试类）
- 原因: 既有集成测试均在顶层 integration 包或本模块包内，避开跨模块规则
- 解决: 移到 com.wherelee.material.integration 顶层包
- 预防: 跨模块集成测试统一放顶层 integration 包
## 2026-11-23 阶段3（身份与目录接口）

### 问题: Mockito any() 匹配原生 long 参数导致拆箱 NPE
- 场景: AuthServiceRegisterTest 验证 grantRole 从未被调用
- 现象: `verify(rbacRepository, never()).grantRole(any(), any(), any())` 抛 NullPointerException（Long.longValue() on null），并污染后续测试类（UnfinishedVerificationException 连锁报错）
- 原因: RbacRepository.grantRole 首参是原生 long，any() 返回 null 拆箱失败
- 解决: 原生参数改用 anyLong()/anyBoolean()
- 预防: 写 matcher 前先核对方法签名，原生类型一律用对应原生 matcher

### 问题: Git Bash 向原生 curl.exe 传中文参数被转成 GBK
- 场景: 冒烟脚本用 curl -d 内联含中文昵称的 JSON
- 现象: 服务端 JSON parse error: Invalid UTF-8 middle byte 0xcc，注册接口 500；脚本文件本身确认是 UTF-8
- 原因: MSYS2 向非 MSYS 原生 Windows 程序（curl.exe）传 argv 时按系统 ANSI 代码页（GBK）转码，内联中文参数字节被改写
- 解决: JSON 体写入文件，用 `curl --data @file` 传参（文件字节不经过 argv 转码）；中文断言用 grep 匹配 UTF-8 响应体不受影响
- 预防: Windows Git Bash 下含中文的请求体一律走文件，不内联进原生命令参数

### 问题: bash UID 为只读变量
- 场景: 冒烟脚本用 UID 存买家 id
- 现象: `UID: readonly variable`，赋值失败导致后续 SQL 拼接错误
- 原因: UID 是 bash 内建只读变量（当前用户 id）
- 解决: 改名 BUYER_ID
- 预防: 脚本变量避开 UID/EUID/PPID 等内建名

### 问题: JwtAuthFilter 构造器变更后旧单测编译失败
- 场景: JwtAuthFilter 增加 RbacRepository 依赖
- 现象: JwtAuthFilterTest 仍用两参构造器，testCompile 失败
- 原因: 构造器签名变更未同步测试
- 解决: 测试补 @Mock RbacRepository，并新增 RBAC 权威/legacy 回退/公开目录/注册放行 4 个用例
- 预防: 改构造器签名时全局 grep `new 类名(` 同步所有调用点

## 2026-11-23 阶段4（交易闭环）

### 问题: BizException 无 conflict() 工厂方法
- 场景: CartCheckoutService 并发兜底分支抛 409
- 现象: 编译失败「找不到符号 conflict(java.lang.String)」
- 原因: 内核 409 一律用 `new BizException(HttpStatus.CONFLICT, msg)`，只有 badRequest/notFound 工厂
- 解决: 改用构造器 + HttpStatus.CONFLICT
- 预防: 抛错前先核对 BizException 已有工厂，不凭印象造方法

### 问题: 冒烟脚本 python 输出中文被 GBK 化导致断言假失败
- 场景: jget 用 python 打印订单标题与期望中文比对
- 现象: actual 显示乱码，标题断言 FAIL，但接口返回本身正确
- 原因: Windows 控制台默认 cp936，python stdout 编码随之 GBK；bash 期望串是 UTF-8
- 解决: 脚本头部 `export PYTHONIOENCODING=utf-8`
- 预防: Windows 下脚本调 python 输出中文一律显式指定 PYTHONIOENCODING

### 问题: 管理员初始密码未改导致退款接口 428
- 场景: 冒烟用种子 admin 直接调退款
- 现象: 退款返回「请先修改初始密码」，订单停在 FULFILLED
- 原因: 种子账号 must_change_password=true，安全拦截先于业务
- 解决: 脚本先登录→PUT /auth/password 改密→重新登录再退款（兼容已改密的重跑）
- 预防: 冒烟涉及种子管理账号时把首登改密纳入脚本流程

### 问题: 退款轮询只等状态导致权益回收断言假失败
- 场景: 整单退款后断言 owned=false
- 现象: 状态已 REFUNDED 但 owned 仍 true、重新加购仍 400
- 原因: 退款确认与权益撤销经发件箱异步解耦，REFUNDED 先到、revoke 后投递（约 1s）
- 解决: 轮询条件改为 status=REFUNDED 且 owned=false 同时满足
- 预防: 异步链路断言要等「最终业务事实」而非中间状态；与集成测试 drainOutbox 同理

## 2026-11-23 阶段5（权益与交付）

### 问题: 旋转水印被 PDFTextStripper 逐字拆行导致断言假失败
- 场景: MaterialPdfFactoryTest 断言每页含「已购 · 张三 · ID7」
- 现象: 提取文本为「已\n购\n \n·\n \n张\n三…」，contains 失败
- 原因: 45° 旋转文本的字符坐标不共线，PDFTextStripper 按位置拆行
- 解决: 断言前 `replaceAll("\s","")` 去空白再比对
- 预防: PDF 文本断言只比对去空白后的语义串，不依赖排版空白

### 问题: ArchUnit 把 identity.api 读端口也挡在 material 模块外
- 场景: MaterialDeliveryService 注入 UserDirectoryQuery 取买家昵称
- 现象: material_should_only_depend_on_platform_and_apis 报 6 处违例
- 原因: 规则用整包 `..modules.identity..` 禁依赖，与规则名「允许 api」的本意不符；course 模块早已消费 identity.api
- 解决: 新增 identityInternal() 谓词（禁内部实现、放行 api），与 commerceInternal() 同构
- 预防: 模块边界规则一律「禁内部实现、放行 api 端口」，新增跨模块读端口时同步谓词

### 问题: Windows 并发懒生成 PDF 时 ATOMIC_MOVE 抛 AccessDeniedException
- 场景: 6 并发下载同一缺失文件，3 个请求 500
- 现象: AccessDeniedException: gen-xxx.tmp -> 目标.pdf；集成测试偶发红
- 原因: Windows 的 ATOMIC_MOVE 不支持覆盖已存在目标，抛的是 AccessDeniedException 而非 FileAlreadyExistsException，原回退捕获漏了它
- 解决: 捕获加 AccessDeniedException；移动失败且目标已存在说明并发线程已落盘，直接读已有文件
- 预防: 跨平台原子落盘的回退分支要覆盖 Windows 异常语义；并发文件生成必须有真并发集成测试

### 问题: 冒烟脚本放在 target/ 被 mvn clean 清除
- 场景: 阶段5 想重跑阶段4 冒烟脚本
- 现象: target/smoke-stage4.sh 不存在
- 原因: clean 生命周期删除 target 全目录，脚本属资产不应放构建产物目录
- 解决: 冒烟脚本迁至 tools/smoke/ 持久目录（smoke-stage5.sh）
- 预防: 可复用验证脚本一律放 tools/，target 只放构建产物

### 问题: 集成测试 completePayment 固定用 fixture 学生导致他人订单 404
- 场景: 注册用户下单后调 completePayment(orderNo)
- 现象: pay 内 requireOwned 抛「订单不存在」
- 原因: 支撑类 pay() 绑定 fx.studentId()，与订单归属不一致
- 解决: 该用例显式 paymentService.pay(userId, orderNo) + acceptVerified + drainOutbox
- 预防: 复用测试支撑方法前核对隐含的身份参数

### 问题: 演示 PDF 封面行重叠
- 场景: 目检试读/下载 PDF 渲染图
- 现象: 「分类/学段/学科」行与简介行叠印
- 原因: drawLine 返回原 y 不推进行距，封面meta行后只留 10pt 而字号 12pt
- 解决: drawLine 后按字号显式推进行距（封面 20pt、正文标题 24pt）
- 预防: PDF 排版改动必须渲染成图目检，文本断言看不出叠印

### 问题: 新增 Element Plus 图标未注册导致组件解析警告
- 场景: MaterialDetail/LearningSpace 使用 Download 图标
- 现象: 控制台「Failed to resolve component: Download」
- 原因: 项目图标在 main.js 手工全局注册，非全量自动导入
- 解决: main.js 导入并注册 Download
- 预防: 用新图标前先查 main.js 注册清单

## 2026-11-23 阶段6（运营端）

### 问题: AccessDeniedException 被吞成 500 而非 403
- 场景: 无权限点身份调用 @PreAuthorize 管理端接口
- 现象: 期望 403「没有权限执行该操作」，实际 500
- 原因: Spring Security 的 AccessDeniedException 冒泡到通用 Exception 兜底分支
- 解决: GlobalExceptionHandler 显式 @ExceptionHandler(AccessDeniedException) 返回 403 固定文案
- 预防: 鉴权异常必须有独立处理分支，不能落进通用 500 兜底

### 问题: Git Bash 原生 curl 把命令行中文按 GBK 转码
- 场景: 冒烟脚本管理端 keyword 中文筛选（检查 27/28）
- 现象: 服务端收到乱码关键词，筛选 total=0
- 原因: Windows Git Bash 的 curl 按 ANSI(GBK) 编码 argv 中文，且读不了 MSYS 临时路径
- 解决: 关键词写入相对路径 UTF-8 文件，python `urllib.parse.quote` 预编码后拼 URL
- 预防: 含中文的查询参数一律走 UTF-8 文件 + 百分号编码，不直接放 curl argv

### 问题: MockMvc 响应中文按默认字符集读取乱码
- 场景: 集成测试断言审计 errorMessage 含「角色不存在」
- 现象: 断言拿到「????GHOST」
- 原因: `getContentAsString()` 用默认 ISO-8859-1，中文被破坏（DB 与写入路径均为 utf8mb4，无损）
- 解决: 解析响应树改用 `getContentAsString(StandardCharsets.UTF_8)`
- 预防: MockMvc 凡断言/解析中文响应体必须显式 UTF-8

### 问题: 新表与跨模块读未登记导致表归属测试红
- 场景: 新增 t_refund_request 与 RefundRequestRepository join t_user
- 现象: TableOwnershipTest 报「访问未登记归属的表」「跨归属访问 t_user」
- 原因: 每张表须登记 OWNER，跨模块 SQL join 须登记 READ_MODEL_EXEMPTIONS
- 解决: OWNER 增 `t_refund_request→commerce`；豁免增 `RefundRequestRepository→{t_user}`（测试既有的登记机制，非放宽断言）
- 预防: 新增表/跨模块读模型时同步登记归属与豁免，这是架构守卫的正式入口

### 问题: 注册即授 BUYER 导致角色断言漏项
- 场景: 集成测试断言超管角色集合
- 现象: containsExactly("SUPER_ADMIN") 失败
- 原因: register 默认授 BUYER，超管同时持有 BUYER+ SUPER_ADMIN
- 解决: 改 containsExactlyInAnyOrder("BUYER","SUPER_ADMIN")
- 预防: 角色断言先核对注册默认授角逻辑

### 问题: 退款申请并发同意的重复退款防护
- 场景: 6 线程同时 approve 同一退款申请
- 现象: 需保证恰好退款一次
- 原因: 先查后改存在竞态
- 解决: 条件抢占 `UPDATE ... WHERE id=? AND status='PENDING'`，命中才调 RefundService.refund；退款抛异常则 revertToPending 回滚抢占
- 预防: 一次性资金动作一律「条件更新抢占 + 失败回滚」，并以真并发集成测试断言恰好一次

### 问题: 浏览器合成点击不触发 Vue 事件处理器
- 场景: 浏览器 E2E 点登录/退出/角色按钮均无反应
- 现象: click 工具报成功但无网络请求、对话框不弹
- 原因: 自动化合成点击未派发 Vue 监听的真实事件
- 解决: evaluate_script 内用原生 `element.click()` 驱动；输入框用原生 value setter + dispatch input 事件触发响应式
- 预防: 本环境浏览器交互优先原生 DOM 事件，表单提交可用回车或原生 click

### 问题: Git Bash /tmp 与 Windows python 路径不一致
- 场景: bash 重定向到 /tmp 后 python 读同路径
- 现象: python FileNotFoundError（curl 却 HTTP 200）
- 原因: MSYS /tmp 映射到真实临时目录，Windows python 把 '/tmp' 当字面盘符路径
- 解决: 改用管道喂 python stdin，或落到工作目录相对路径
- 预防: 跨 bash/python 交换文件用相对路径或 stdin，不共用 /tmp 绝对路径

## 2026-11-23 阶段7（质量/安全/部署准备）

### 问题: CORS 未知来源在集成上下文返回 403 而本地直连返回 200
- 场景: 新增 SecurityContractIntegrationTest.corsRejectsUnknownOrigin
- 现象: MockMvc 全量上下文断言 status().isOk() 失败，实际 403；本地 curl 直连 8084 却是 200（仅无 ACAO 头）
- 原因: 集成上下文加载完整 Spring Security 过滤链，CorsFilter 对非白名单 Origin 直接 403 拒绝；本地直连未经同等代理/过滤路径，表现为放行但不回显 ACAO。两种行为都满足安全不变量——不授予跨域许可
- 解决: 断言只校验安全不变量 header().doesNotExist("Access-Control-Allow-Origin")，不再绑定具体状态码
- 预防: 安全契约测试断言「不变量」（是否泄露许可头）而非「实现细节」（具体状态码），避免被过滤链装配差异误伤

### 问题: 退款并发测试在 CI 偶发全失败（时序前提过强）
- 场景: stage-6 推送后 CI backend 失败（run 45157702462），本地隔离与全量均 5/5 通过
- 现象: concurrentRefundsCreateOneRefundAndRevokeEntitlementOnce 所有并发调用 error「当前订单状态不能退款」
- 原因: CI CPU 争用下赢家线程先提交 REFUNDED，晚到线程读到的订单已是终态，被状态守卫合法拒绝。原断言 allSatisfy(error==null) 把「所有调用都在 FULFILLED 窗口内到达」当成必然前提，实际取决于线程调度
- 解决: 改为 anySatisfy(至少一笔成功) + allSatisfy(任何失败必须是该合法守卫拒绝)；安全不变量 t_refund=1、权益 REVOKED=1、订单 REFUNDED 全部保留不变
- 预防: 并发测试只断言与调度无关的安全不变量（恰好一笔/终态一致），把「调用成败分布」视为调度结果而非正确性判据；这不是放宽断言掩盖缺陷，而是修正过强的时序前提

### 问题: 容器重启丢失已上传资料 PDF（持久化缺口）
- 场景: 审查 compose.yml 部署资产
- 现象: 后端资料存储根 ./data/materials 落在容器可写层，未挂卷，容器重建即丢
- 原因: 课程时代只有 COS 视频（外部对象存储），资料商城改为本地 PDF 存储后未补持久卷
- 解决: compose 新增 material-data 命名卷挂 /app/data/materials + 环境变量 MATERIAL_STORAGE_ROOT；Dockerfile 预创建该目录并 chown app（命名卷挂载点需属主正确）；.env.docker.example 注明资料持久化、COS 为遗留可选
- 预防: 引入新的本地落盘能力时同步审查部署持久化，不能只改应用配置

## 2026-11-23 阶段8（面试包装）

### 问题: 旧 README 口径与现状相反（文档幻觉风险）
- 场景: 重写 README 前核对现状
- 现象: 旧 README 写「不开放自助注册」「不引入完整 RBAC」，而代码已实现买家自助注册 + 5 角色 12 权限点 RBAC
- 原因: README 停留在课程时代，未随资料商城改造更新
- 解决: 逐条对照控制器/种子/AuthService 源码重写，RBAC 映射、注册角色、交付水印口径全部以源码为准
- 预防: 包装文档前必须读源码核实现状，不信旧文档与记忆（business-drift-check「去读，别信文档」）

### 问题: 演示脚本 UI 标签易凭印象写错
- 场景: 编写退款审批走查步骤
- 现象: 初稿写「驳回/通过」「退款审批页」，实际组件是 AdminOrders 订单页内「同意/拒绝」，拒绝弹窗必填理由展示给买家
- 原因: 凭流程印象而非组件实际文案
- 解决: grep 真实 Vue 组件核实每个动作标签后再落文档
- 预防: 演示脚本的按钮/页面名称须对照组件源码，避免演示时找不到对应 UI

### 问题: 运行库演示数据与 pristine 种子不一致
- 场景: HTTP sanity 核对目录标题
- 现象: 运行库标题（如「小学语文阅读理解讲义」）与种子（「小学语文阅读提分讲义」）不同
- 原因: 种子仅在目录为空时写入，运行库经阶段6冒烟/E2E 运营改动
- 解决: README 描述 fresh-start 种子口径，演示脚本以实际 UI 为准，二者不混淆
- 预防: 区分「种子初始态」与「运行期被改动态」，文档注明口径

## 2026-11-23 质量打磨（审查整改）

### 问题: compose 默认部署时 Mock 支付真实可用（审查 B1）
- 场景: 按仓库 `compose.yml` 部署
- 现象: 未设 prod profile，`PAYMENT_MOCK_ENABLED` 默认 true，学员可「模拟支付成功」零成本拿权益
- 原因: Mock 开关默认开启，安全守卫只在 prod 拒绝 Mock
- 解决: Mock 默认关闭（`application.yml`/compose 默认 false）；`PaymentSafetyGuard` 对 `provider=mock && !mockEnabled` 启动失败；本地 start-local 显式开启；prod 关闭 springdoc；补齐 ALIPAY_* 透传与部署文档
- 预防: 安全开关一律 fail-closed；新增配置默认值必须按「最安全」取值

### 问题: 运营订单列表资料订单标题为空、搜不到（审查 M1）
- 场景: 运营端订单列表/关键词检索
- 现象: 购物车订单商品名为空，按资料标题搜索无结果
- 原因: 列表 SQL 只读 `series_title_snapshot`，资料订单写的是 `product_title_snapshot`
- 解决: 查询与过滤改用 `COALESCE(product_title_snapshot, series_title_snapshot)`，并补关键词检索列；新增 `AdminOrderListIntegrationTest`
- 预防: 通用化字段（product_*）上线时同步改造所有读取方，避免只改写入方

### 问题: 参数校验异常回落 500（审查 m1）
- 场景: 空关键词搜索、分页参数非数字、非法 JSON
- 现象: 返回 500 兜底而非 400
- 原因: 只处理了 `MethodArgumentNotValidException`
- 解决: 补 `HandlerMethodValidationException`/`MethodArgumentTypeMismatch`/`MissingServletRequestParameter`/`HttpMessageNotReadable` 的 400 映射；新增 `ApiErrorContractIntegrationTest`
- 预防: 参数错误契约要有集成测试覆盖，不能只测正常路径

### 问题: X-Forwarded-For 可伪造绕过登录 IP 限流（审查 M2）
- 场景: compose 的 web nginx / Caddy 反代
- 现象: nginx 用 `$proxy_add_x_forwarded_for` 追加，应用取首个值，客户端自带 XFF 即可伪造 IP
- 原因: 追加语义与「取首个值」不匹配
- 解决: 边缘代理改为覆盖写入 `X-Forwarded-For $remote_addr`（Caddy 用 `header_up {remote_host}`）；解析器注释明确契约；补 `ClientIpResolverTest`
- 预防: 限流类安全字段必须由边缘覆盖写入，不信任客户端可写头

### 问题: 备份不含资料 PDF（审查 M3）
- 场景: 服务器备份/恢复
- 现象: 只备份 MySQL，恢复后运营上传的资料文件丢失
- 原因: 引入本地 PDF 存储后未扩展备份
- 解决: `deploy/btry-backup.sh` 增资料目录 tar.gz + 保留期清理 + 恢复步骤注释；`tools/backup-mysql.ps1` 增 `-MaterialRoot` 打包
- 预防: 新增本地落盘能力时同步备份与恢复演练

### 问题: 订单列表 N+1（审查 m3）
- 场景: 我的订单分页
- 现象: 每单再查明细与权益
- 原因: view() 内逐单 hasActive
- 解决: 新增 `OrderItemRepository.findByOrderIds` 与权益批量端口 `EntitlementQuery.findActiveIds`（与 hasActive 同口径），列表一次预取；新增 `OrderListOwnedIntegrationTest`
- 预防: 列表路径禁止逐行查询，批量接口与单判定保持同一 SQL 谓词

### 问题: 遗留运营端降权不生效（审查 M4）
- 场景: 撤销某账号 RBAC SUPER_ADMIN 后
- 现象: 其仍可修改系列上下架/发教师邀请（只校验 legacy `t_user.role=ADMIN`）
- 原因: 遗留控制器未迁移到权限点
- 解决: 系列治理改 RBAC `hasRole('SUPER_ADMIN')`（访问集合不变，避免把权限扩到 OPERATOR），教师邀请改 `user:manage`；新增 `LegacyAdminRbacIntegrationTest` 验证撤权即 403
- 说明: 若改用 `material:read/sale` 会顺带把系列治理开放给 OPERATOR，属未经授权的权限扩张，故选择与 legacy ADMIN 等价的最窄 RBAC 角色
- 预防: 授权判定只认 RBAC 权限点，legacy 角色仅作兼容回退，不得作为管理端唯一门禁

### 问题: 退款按订单查询缺索引（审查 m4）
- 场景: 订单详情/客服/对账按 order_id 查退款
- 现象: V15 删除 `uk_refund_order` 后 order_id 无索引，全表扫描
- 原因: 迁移升级为 attempt 维度时未补普通索引
- 解决: 新增 `V24__refund_order_index.sql` 加 `idx_refund_order(order_id)`
- 预防: 删除唯一键/改查询维度时同步评估索引覆盖
