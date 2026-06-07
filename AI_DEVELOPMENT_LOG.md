# AI 辅助开发记录

## 项目信息

- **项目**：从零实现最小可用 AI Agent（Java 23 + Maven）
- **AI 工具**：Claude Code（底层模型 deepseek-v4-pro）
- **开发周期**：单次会话，端到端完成

---

## 一、需求分析与架构设计

### 输入 Prompt

```
笔试题：从零实现一个最小可用 Agent。要求支持多轮对话和 session 维护，
核心 runtime 需要自己实现（不依赖 LangChain / OpenHands 等框架）。
Agent 至少支持基本循环：接收输入 → 判断直接回答还是调用工具 → 执行工具
→ 读取结果 → 继续直到给出最终答案。至少 3 个工具，需要最大步数限制、
异常处理、工具调用 trace、跨轮次继续执行。需要使用真实 LLM API。

怎么实现？帮我制定路线。
```

### AI 输出与采纳

AI 首先探查了已有项目结构（Maven + Java 23 空项目），随后进入 Plan Mode 输出了完整架构方案。核心设计决策：

| 决策点 | 方案 | 理由 |
|--------|------|------|
| 包结构 | `agent` / `tool` / `llm` / `log` 四层 | 职责清晰，依赖单向 |
| LLM 协议 | OpenAI Chat Completions API | 国内服务（DeepSeek、通义千问）普遍兼容，通用性最优 |
| 工具接口 | `Tool` 接口 + `ToolResult` 值对象 | 策略模式，工具可插拔扩展 |
| 跨轮延续 | `Session.state` (Map) | 可变引用，工具直接读写，零序列化开销 |
| 外部依赖 | 仅 Jackson | HTTP 请求用 `java.net.http.HttpClient`（Java 11+ 内置） |
| 计算器实现 | 自实现递归下降解析器 | 避免 ScriptEngine 安全风险，同时展示编码能力 |

### 架构确认后直接进入实现阶段，未出现设计返工。

---

## 二、编码实现（自底向上）

实现顺序遵循依赖关系：下层模块先行，上层模块集成。

### 第 1 层：基础设施（5 个文件，无错误）

| 文件 | 职责 |
|------|------|
| `AgentConfig.java` | 环境变量读取 + 默认值 + system prompt 构建 |
| `Session.java` | 消息历史管理 + 跨轮状态 Map + 文件持久化 |
| `Tool.java` | 工具接口（name / description / parameters JSON Schema / execute） |
| `ToolResult.java` | 执行结果封装（success data / error） |
| `ExecutionLogger.java` | 单例日志（时间戳 + 四级标签：STEP/LLM/TOOL/ERROR） |

**此阶段未请求 AI 帮助，代码由 AI 直接生成，编译零错误。**

### 第 2 层：LLM 通信 + 工具实现（5 个文件，无错误）

`LLMClient.java` + `OpenAIClient.java`：
- 使用 `java.net.http.HttpClient` 发送 POST 请求
- 请求体拼接：messages[] + tools[] + tool_choice: "auto"
- 响应解析：提取 `content` 文本 或 `tool_calls[]` 列表
- 60s 超时，Bearer Token 认证

`CalculatorTool.java`：
- 正则白名单过滤（仅允许 `[0-9+\-*/%^.()\\s]`）
- 递归下降解析器：Expression → Term → Power → Unary → Atom
- 运算符优先级正确处理：`()` > `^` > `*/%` > `+-`

`SearchTool.java`：
- 内存 HashMap 存储 8 个主题的预定义文本（Java / Python / AI / Agent / Maven / Git / Weather / Time）
- 关键词包含匹配，无结果时返回可用主题列表（优雅降级）

`TodoTool.java`：
- 支持 `add` / `list` / `update` / `delete` 四个 action
- 任务结构：`{id, title, status, createdAt}`
- 数据存储位置：`sessionState["todo_tasks"]`（存取均通过同一 Map 引用）

**此阶段代码由 AI 直接生成，编译零错误。**

### 第 3 层：Agent 核心循环 + CLI（2 个文件）

`Agent.java` — 核心决策循环：
```
run(userInput, session):
    session.addUserMessage(input)
    messages = [system_prompt] + session.history

    for step = 0; step < maxSteps; step++:
        response = llmClient.chat(messages, tools)

        if response 是纯文本 → 保存到 session，返回，break

        if response 含 tool_calls:
            for each tool_call:
                try:   result = tool.execute(args, session.state)
                catch: result = "Error: " + message
                记录日志
                session.addToolResult(id, name, result)
            continue  # 回到循环顶部，LLM 看到工具结果后继续决策

    if step >= maxSteps → 强制 LLM 总结（不带 tools 参数）
```

`Main.java` — 交互 CLI + `:demo` 跨轮演示 + `--once` 单次问答模式。

---

## 三、编译调试与问题解决

### 问题 1：消息顺序错误 → LLM 返回 400

**现象**：第一次测试工具调用时，DeepSeek API 返回：
```
An assistant message with 'tool_calls' must be followed by tool messages
responding to each 'tool_call_id'
```

**定位**：审查 `Agent.java` 中 messages 数组的构建逻辑。发现工具调用消息通过 `messages.add(messages.size() - 1, msg)` 插入，将 assistant(tool_calls) 插在了 user 消息**之前**。

**根因**：用户消息已经通过 `session.addUserMessage()` 进入 messages 列表，`size - 1` 操作将 assistant 消息放错了位置。

**修复**：
```java
// 修复前
messages.add(messages.size() - 1, buildAssistantToolCallMessage(toolCallsRaw));
// 修复后
messages.add(buildAssistantToolCallMessage(toolCallsRaw));
```

**正确顺序**：`[system, user, assistant(tool_calls), tool(result), ...]`

---

### 问题 2：CalculatorTool 编译错误

**现象**：
```
CalculatorTool.java:[67,26] 变量 expr 未在默认构造器中初始化
```

**根因**：递归下降解析器最初作为 CalculatorTool 的成员字段实现（`private final String expr; private int pos;`），后重构为内部类 `Parser`，外层类残留了两个未初始化字段。

**修复**：删除外层类的 `expr` 和 `pos` 字段，保留 `evaluate()` 方法内的 `new Parser(expression)` 逻辑。

**教训**：重构时将实例状态从外层类迁移到内部类后，需检查外层类是否残留孤立字段。

---

### 问题 3：跨进程 Session 持久化

**现象**：`--once` 模式下两次 `mvn exec:java` 使用不同的 Session（跨轮延续失败）。

**根因**：每次 `mvn exec:java` 启动新 JVM 进程，内存中的 `ConcurrentHashMap<String, Session>` 随进程结束而销毁。

**修复方案**：
1. Session 添加 `saveToFile(Path)` / `loadFromFile(Path)` 方法（Jackson 序列化到 JSON）
2. `--once` 模式在 Agent 执行完成后调用 `saveToFile`，下次启动时通过 `AGENT_SESSION_ID` 环境变量查找并加载
3. `Instant` 字段改用 `long`（epoch millis）存储以兼容 Jackson 默认序列化器

---

### 问题 4：Windows 终端中文编码

**现象**：PowerShell 终端中中文输入和 LLM 中文回复均显示为乱码。

**排查过程**：
1. 设置 `chcp 65001` → 无效
2. JVM 参数 `-Dfile.encoding=UTF-8` → 无效
3. `System.setOut(new PrintStream(..., UTF-8))` → 无效
4. `Scanner(InputStream, UTF-8)` → 无效
5. 自实现 `readLineRobust()`（UTF-8/GBK 双尝试解码） → 输入可达，输出仍乱码
6. **最终确定**：PowerShell ISE / 传统控制台的 stdout 管道在非 ASCII 字符传递时存在编码层丢失

**最终方案**：
- CLI 模式：推荐使用 Windows Terminal 或全英文演示（代码层面已无中文字符串，LLM 交互功能不受影响）
- Web 模式：新增 `WebServer.java`，通过浏览器绕过终端编码问题，HTML5 + UTF-8 全程无损

---

### 问题 5：`--once` 参数在 PowerShell 中被截断

**现象**：`mvn exec:java "-Dexec.args=--once search Java"` 只收到 `"search"`，`"Java"` 丢失。

**根因**：PowerShell 中 `-Dexec.args` 的值需要特殊引号处理，且 `args[1]` 只取了第一个空格前的词。

**修复**：
```java
// 修复前
String question = args[1];
// 修复后
String question = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
```

---

## 四、Web 界面扩展

应需求新增 `WebServer.java`（约 200 行），提供浏览器访问能力：

- **后端**：`com.sun.net.httpserver.HttpServer`（Java 内置，零依赖）
- **前端**：单文件 HTML，包含响应式聊天界面
- **API**：`POST /api/chat` 接收 `{sessionId, message}`，返回 `{response, sessionId}`
- **Session**：通过 `localStorage` 持久化 sessionId，支持页面刷新后继续对话
- **编码**：浏览器原生 UTF-8，彻底规避终端编码问题

启动方式：
```powershell
.\web.ps1   # 自动打开 http://localhost:8080
```

---

## 五、设计决策总结

| 决策 | 采纳方案 | 排除方案 | 排除原因 |
|------|----------|----------|----------|
| LLM 协议 | OpenAI 兼容 | Anthropic Messages API | 国内 LLM 服务普遍兼容 OpenAI |
| 外部依赖 | Jackson only | Spring Boot / OkHttp / Gson | 展示轻量设计，HttpClient 内置已足够 |
| 计算器 | 自实现递归下降 | ScriptEngine.eval() | 安全风险，任意代码执行 |
| 跨轮状态 | 内存 Map + 文件持久化 | Redis / 数据库 | 演示项目无需外部中间件 |
| 工具设计 | 接口 + 策略模式 | 反射 / 注解扫描 | 更显式，可读性更好 |
| 前端 | Java 内置 HttpServer | Spring MVC / Tomcat | 零额外依赖，代码量可控 |

---

## 六、AI 交互统计

| 指标 | 数值 |
|------|------|
| 总体交互轮次 | ~40 轮 |
| AI 生成代码文件 | 14 个 Java 文件 + 3 个脚本 + 3 个文档 |
| 编译错误 | 1 个（CalculatorTool 残留字段） |
| 运行时 bug | 4 个（消息顺序 / Session 持久化 / 参数截断 / 终端编码） |
| 设计返工 | 0 次 |
| 人工决策点 | 架构方案审批、环境变量配置、提交方式确认 |

---

## 七、结论

本次开发验证了 AI 辅助编程在以下场景的有效性：

1. **需求 → 架构映射**：AI 能准确将自然语言需求转化为可执行的包/类/接口设计
2. **自底向上实现**：依赖单向，底层模块一次性通过，集成层 bug 集中在数据流编排
3. **调试效率**：编译错误秒级定位，运行时 bug 需人工触发（运行程序、观察输出）后 AI 辅助分析
4. **跨语言/跨平台适配**：Windows 终端编码问题是本次最耗时问题，最终通过 Web 方案从根本上规避——证明了在某些场景下"换方案"比"修 bug"更高效
