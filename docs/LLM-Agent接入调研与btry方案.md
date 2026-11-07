# LLM Agent 接入调研与 btry 方案

更新日期：2026-11-05

## 1. 调研结论

当前项目里的 LLM Agent 通常不是“一个会自己操作一切的数字员工”，而是以下一种或几种形态：

| 形态 | 常见做法 | 适合场景 |
|---|---|---|
| 产品内嵌助手 | 悬浮聊天窗、侧边栏、页面内 Copilot | 客服、学习问答、写作辅助 |
| 后端 AI 服务 | 现有后端代理模型调用，前端只拿 SSE/JSON | 所有面向用户的 AI 能力 |
| 工具调用型 Agent | 模型通过函数调用查询订单、课程、进度 | 有明确数据和动作边界的业务助手 |
| 工作流编排 | LangGraph、Spring AI Graph、Temporal 等 | 多步骤、长流程、需要人工审批的任务 |
| 后台批处理 Agent | 定时摘要、分类、标签、内容审核 | 非实时、可异步的内容处理 |
| MCP 工具服务 | 把内部 API/数据暴露成 MCP 工具 | 内部开发助手、跨工具 Agent |
| 研发/运维 Agent | 代码审查、故障分析、日志查询 | 开发流程，不是终端产品 |
| 多 Agent 协作 | 规划、执行、评审分工 | 复杂任务，当前项目通常不需要 |

关键趋势：

- 大多数成功项目先做**单助手 + 少量工具 + 人工兜底**，不是一上来做多 Agent。
- Agent 的价值主要在“调用现有业务能力”，不是在项目里再建一个独立大脑。
- 权限、审计、成本、失败降级比模型本身更重要。

## 2. btry 适合什么

btry 是 10～20 人教培机构的内部录播平台，已有课程、订单、支付、学习进度和管理后台。最适合的 AI 形态不是“全能 Agent”，而是三个受限助手：

### 2.1 学员客服助手（首选）

入口：`/learn/` 右下角助手。

能回答：

- 我的订单现在是什么状态？
- 我买过的课在哪里？
- 这个系列有哪些章节？
- 我学到哪了？
- 视频为什么看不了？
- 退款规则是什么？

工具（只读）：

- `getMyOrderStatus(orderNo)`
- `getMyLearningProgress(seriesId/videoId)`
- `getCourseOutline(seriesId)`
- `searchFaq(query)`
- `getPlaybackDiagnosis(videoId)`（只返回诊断结果，不返回 COS 密钥）

写操作只允许：

- `createSupportTicket`（创建人工工单，需用户确认）

这些工具必须从 JWT 当前用户出发做归属校验，不能接受任意 `userId`。

### 2.2 教师内容 Copilot（第二阶段）

入口：教师系列编辑页。

用途：

- 根据章节标题生成系列简介、章节摘要；
- 生成练习题草稿；
- 检查课程描述是否完整。

边界：

- 只生成草稿，不自动发布；
- 教师确认后再保存；
- 不读取其他教师的课程。

### 2.3 管理员运营助手（第三阶段，只读优先）

入口：管理员订单/异常页。

用途：

- “这单为什么卡住？”
- “这个支付事件为什么失败？”
- “退款重试到第几次了？”

工具只读：

- `getOrderTimeline(orderNo)`
- `getPaymentEvents(orderNo)`
- `getRefundStatus(orderNo)`
- `listStuckOrders()`

**不要给 Agent 退款、关单、补履约、改价格的写工具。** 这些继续由管理员在现有 UI 中操作，Agent 只做解释和定位。

## 3. 推荐架构

### 3.1 形态

在现有 Spring Boot 后端中增加一个模块：

```text
com.wherelee.edu.modules.assistant
  api/           对外端口
  chat/          会话、消息、SSE
  tools/         业务工具适配器
  provider/      模型供应商适配
```

前端增加一个浮动助手组件，不新增独立前端应用。

不要为了一个助手拆 Python 服务，也不要引入多 Agent 框架。当前单实例、10～20 人规模，Spring Boot 内一个受限工具循环足够。

### 3.2 调用链

```text
浏览器
  → /api/assistant/chat（JWT）
  → AssistantService
  → 模型（流式）
  → 工具调用（最多 3 轮）
  → 业务 API/只读查询
  → SSE 返回
```

模型 API Key 只在后端环境变量中，前端永远不接触。

### 3.3 Java 技术选型

可选：

- **Spring AI**：与 Spring Boot 集成自然，支持 ChatClient、工具调用、RAG、MCP；适合当前项目。
- **LangChain4j**：Java 生态成熟，也适合工具调用和 RAG。
- 直接 HTTP + JSON Schema 工具调用：依赖最少，但需要自己处理流式、工具循环和供应商差异。

建议先做一个小型 provider 适配层，不让业务代码直接依赖某一家模型 SDK。配置使用：

```text
LLM_BASE_URL=
LLM_API_KEY=
LLM_MODEL=
LLM_MAX_TOOL_ROUNDS=3
LLM_DAILY_TOKEN_BUDGET=
```

### 3.4 知识库

第一阶段不要上向量数据库。btry 的 FAQ、课程简介、章节标题数量很小，先用：

- MySQL 关键词/全文检索；
- 固定 FAQ 表；
- 课程元数据查询。

等有课程字幕/讲义，并且确实出现“语义检索比关键词明显更好”的证据，再引入 embedding/vector store。

## 4. 安全与成本

必须做：

- 工具白名单、参数校验、当前用户数据隔离；
- 读工具优先，写工具必须人工确认；
- 不把数据库连接、COS 密钥、支付密钥放入提示词；
- 不把完整回调报文、密码、JWT 写入模型上下文；
- 提示注入防护：系统提示词固定业务边界，工具结果按不可信数据处理；
- 输出过滤：不返回内部堆栈、SQL、密钥和他人数据；
- 每用户/每会话限流，限制上下文长度、工具轮数、输出 token；
- 记录 conversationId、traceId、工具名、参数摘要、耗时、token 用量和失败原因；
- 模型不可用时降级为 FAQ/人工入口，不影响学习、支付主链路。

OWASP LLM Top 10 中与 btry 最相关的是：提示注入、敏感信息泄露、过度代理、无界消耗。不要给 Agent“直接执行任意动作”的能力。

## 5. 分阶段计划

### Phase 1：学员客服助手（建议先做）

- FAQ + 订单状态 + 学习进度 + 课程目录只读工具；
- 一个聊天窗、SSE 流式输出；
- 无写操作，退款只解释政策并引导人工；
- 20～30 条回归问题，验证工具选择、拒答和数据隔离；
- 观察 token 成本、响应时延和人工接管率。

### Phase 2：教师内容 Copilot

- 生成简介/摘要/题目草稿；
- 教师确认后保存；
- 不自动发布，不跨教师读取。

### Phase 3：管理员运营助手

- 只读订单/支付/退款时间线；
- 解释异常，不给退款/关单写权限；
- 复杂动作仍走现有管理员 UI。

### Phase 4：按需增强

- 课程字幕和讲义具备后，再做 RAG；
- 多步骤任务确实出现后，再考虑工作流编排；
- 需要给内部开发助手复用工具时，再考虑 MCP。

## 6. 明确不做

- 不做“自动退款/自动关单/自动改价”的 Agent；
- 不让模型直连数据库或拿到管理员 token；
- 不为 10～20 人规模引入多 Agent、MQ、向量数据库或独立 Python AI 服务；
- 不把通用聊天机器人放在未登录首页当卖点；
- 不用 Agent 替代现有确定性支付状态机和人工兜底。

## 7. 结论

btry 最合理的引用方式是：**在现有产品里做一个受限的学员客服/学习助手，后端代理模型，工具只读优先，写操作人工确认，先 FAQ + 业务查询，暂时不上向量库和多 Agent。**

这能产生真实价值，又不会破坏当前支付和学习主链路的确定性。

## 8. 参考

- Anthropic, Building effective AI agents: https://www.anthropic.com/research/building-effective-agents
- Spring AI Reference: https://docs.spring.io/spring-ai/reference/
- LangChain4j: https://docs.langchain4j.dev/
- Model Context Protocol: https://modelcontextprotocol.io/
- LangGraph overview: https://docs.langchain.com/oss/python/langgraph/overview
- OpenAI Agents SDK: https://github.com/openai/openai-agents-python
- OWASP Top 10 for LLM Applications: https://owasp.org/www-project-top-10-for-large-language-model-applications/
