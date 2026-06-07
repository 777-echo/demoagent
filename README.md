# Minimal AI Agent — 从零实现

一个从零构建的最小可用 AI Agent，支持多轮对话、工具调用、Session 管理、跨轮状态延续。

## 系统架构

```
用户输入 → Main.java (CLI 入口)
              ↓
         Agent.java (核心决策循环)
              ↓
    ┌─────────┼─────────┐
    ↓         ↓         ↓
  LLM调用   工具执行   日志追踪
(OpenAI API)(Tool接口)(ExecutionLogger)
              ↓
         Session.java (会话 & 跨轮状态)
```

## 核心设计

### Agent 决策循环（Agent.java）

```
while (step < maxSteps):
    1. 构建 messages = system_prompt + session.history
    2. 调用 LLM API（带 tools 定义）
    3. LLM 返回:
       a. 文本回答 → 保存到 session，返回给用户，结束
       b. tool_calls → 执行工具 → 结果加入 messages → step++ → 继续循环
    4. step >= maxSteps → 强制 LLM 总结已有信息
```

### Session 与跨轮延续

每个 Session 包含：
- `messages: List<Message>` — 完整对话历史（OpenAI 格式）
- `state: Map<String, Object>` — 跨轮持久化状态

**跨轮延续的关键**: `state` 是一个可变的 Map，工具可以将数据写入其中。例如 TodoTool 将任务列表存储在 `state["todo_tasks"]` 中，下一次用户追问时可以直接读取。

```
Round 1: 用户创建3个学习任务
  → Agent 调用 todo.add ×3
  → 任务存入 session.state["todo_tasks"]

Round 2: 用户问"第一个任务进度如何？"
  → Agent 调用 todo.list
  → 从 session.state["todo_tasks"] 读取任务列表
  → 基于已有状态回答（而不是重新开始）
```

### Memory/State 的召回时机与放置方式

| 时机 | 方式 |
|------|------|
| **写入** | 工具执行结果直接写入 `session.state`（如 TodoTool 写 `todo_tasks`） |
| **召回** | 每轮对话开始时，Agent 加载 session 的全部 messages 历史 + state；工具执行时从 state 读取已有数据 |
| **生命周期** | Session 在内存中维护，由 `ConcurrentHashMap<String, Session>` 管理，直到进程退出 |

## 3 个工具

### 1. CalculatorTool — 安全计算器
- 表达式: `(2+3)*4^2`
- 实现: 递归下降解析器，仅允许 `+-*/%^.()` 和数字
- 不使用 ScriptEngine（安全性考虑）

### 2. SearchTool — Mock 搜索
- 基于关键词匹配返回预定义知识库
- 覆盖: Java、Python、AI、Agent、Maven、Git、天气、时间

### 3. TodoTool — 任务管理（跨轮状态核心）
- `add`: 创建任务 → `{id, title, status, createdAt}`
- `list`: 列出所有任务（支持按 status 过滤）
- `update`: 更新任务状态/标题
- `delete`: 删除任务
- **任务数据存在 session.state 中，跨轮不丢失**

## 项目结构

```
src/main/java/com/example/
├── Main.java                  CLI 入口 + 交互式对话 + 跨轮演示
├── agent/
│   ├── Agent.java             核心 Agent 循环
│   ├── AgentConfig.java       配置（从环境变量读取）
│   └── Session.java           会话管理 + 消息历史 + 跨轮状态
├── tool/
│   ├── Tool.java              工具接口
│   ├── ToolResult.java        工具执行结果
│   ├── CalculatorTool.java    安全计算器
│   ├── SearchTool.java        Mock 搜索
│   └── TodoTool.java          任务管理（跨轮状态）
├── llm/
│   ├── LLMClient.java         LLM 客户端接口
│   └── OpenAIClient.java      OpenAI 兼容 API 实现
└── log/
    └── ExecutionLogger.java   单例执行日志
```

## 运行方式

### 1. 环境准备

- Java 23+
- Maven 3.8+
- LLM API Key（OpenAI 或兼容服务）

### 2. 设置环境变量

```bash
# 必需
export OPENAI_API_KEY=your_api_key_here

# 可选（默认值如下）
export OPENAI_BASE_URL=https://api.openai.com/v1
export LLM_MODEL=gpt-4o
export MAX_STEPS=10
```

**国内 LLM 服务示例**:
```bash
# DeepSeek
export OPENAI_API_KEY=sk-your-deepseek-key
export OPENAI_BASE_URL=https://api.deepseek.com/v1
export LLM_MODEL=deepseek-chat
```

### 3. 编译运行

```bash
# 编译
mvn clean compile

# 运行
mvn exec:java
```

### 4. 交互命令

```
:help, :h       帮助
:sessions, :ss  列出所有会话
:new, :n        创建新会话
:session <id>   切换会话
:demo, :d       运行跨轮延续演示
:quit, :q       退出
```

## 测试场景

### 单轮工具调用

```
🧑 You > 计算 (15 + 27) * 3 - 8^2
🤖 Agent > [调用 calculator] → (15 + 27) * 3 - 8^2 = 62
```

### 多轮对话 + 跨轮延续

```
═══ Turn 1 ═══
🧑 You > 帮我创建一个学习计划，包含3个任务：学Java、学Python、学AI
🤖 Agent > [调用 todo.add ×3] → 已创建3个任务...

═══ Turn 2 ═══
🧑 You > 第一个任务进度如何？标记为进行中
🤖 Agent > [调用 todo.list] → [调用 todo.update id=xxx status=in_progress]
         → 任务「学Java」已标记为进行中。当前进度: 1/3 进行中...
```

也可以直接输入 `:demo` 自动运行上述跨轮场景。

## 安全设计

- **计算器**: 自实现递归下降解析器，不调用 ScriptEngine，仅允许数学表达式字符
- **JSON 解析**: 异常捕获，防止格式错误导致崩溃
- **工具异常隔离**: 每个工具的异常都会被 catch 并转为错误信息返回给 LLM
- **最大步数限制**: 防止无限循环，默认 10 步

## 执行日志示例

```
[2026-06-07 12:30:01] [INFO] Session: a1b2c3d4 | User: 计算 2+3*4
[2026-06-07 12:30:02] [STEP] [1] Calling LLM...
[2026-06-07 12:30:03] [LLM] Tool call requested → calculator({"expression":"2+3*4"})
[2026-06-07 12:30:03] [STEP] [1] Executing tool: calculator({"expression":"2+3*4"})
[2026-06-07 12:30:03] [TOOL] calculator → 2+3*4 = 14
[2026-06-07 12:30:04] [STEP] [2] Calling LLM...
[2026-06-07 12:30:05] [LLM] Response text: 计算结果为 14
[2026-06-07 12:30:05] [STEP] [2] Final answer received (finish=stop)
```

## 依赖

| 依赖 | 用途 |
|------|------|
| Jackson 2.18.2 | JSON 序列化/反序列化 |
| Java 23 HttpClient | HTTP 请求（内置） |
| exec-maven-plugin | 命令行运行 |

## AI 辅助开发说明

本项目完全使用 Claude Code 辅助开发。开发过程：

1. **需求分析** → 明确 Agent 循环、工具、跨轮延续等核心需求
2. **架构设计** → 设计包结构、类职责、数据流
3. **逐文件实现** → 从底层模型到上层 CLI，逐步构建
4. **编译验证** → 修复编译错误，确认 BUILD SUCCESS
5. **文档编写** → 生成 README

## 演示视频

[下载演示视频](https://github.com/777-echo/demoagent/releases/latest)

> 视频展示了：计算器工具调用、跨轮状态延续（创建任务 → 追问进度）
