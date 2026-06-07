# 终端操作录屏演示脚本

## 录制准备

### 环境检查
```bash
# 确认 Java 版本
java --version
# 预期: Java 23+

# 确认已设置环境变量
echo $OPENAI_API_KEY
echo $OPENAI_BASE_URL
echo $LLM_MODEL
```

### 编译
```bash
cd D:/project/demo607
mvn clean compile
# 预期: BUILD SUCCESS
```

---

## 录屏流程（预计 4-5 分钟）

### 第一部分：项目结构展示（30 秒）

**画面**：IDE 或终端展示项目文件树

**口播**：
> "这是一个从零实现的 AI Agent，使用 Java 23 和 Maven 构建。
> 核心 runtime 完全自实现，不依赖 LangChain 等现成 Agent 框架。
> 包含 agent、tool、llm、log 四个包，共 12 个 Java 文件。"

**操作**：
```bash
# 展示文件结构
find src -name "*.java" | sort
```

**讲解要点**：
- agent/ — 核心循环 + 会话管理
- tool/ — 3 个工具（计算器、搜索、任务管理）
- llm/ — LLM API 客户端
- log/ — 执行日志

---

### 第二部分：启动与基础对话（1 分钟）

**画面**：终端运行 `mvn exec:java`

**口播**：
> "启动后进入交互式对话界面。我先测试一个简单计算，验证 calculator 工具。"

**操作 1 — 计算器工具调用**：
```
🧑 You > 帮我计算 (15 + 27) * 3 - 8 的平方
```

**预期输出**（画面展示）：
```
[2026-06-07 ...] [INFO] Session: xxxxxxxx | User: 帮我计算...
[2026-06-07 ...] [STEP] [1] Calling LLM...
[2026-06-07 ...] [LLM] Tool call requested → calculator({"expression":"(15+27)*3 - 8^2"})
[2026-06-07 ...] [STEP] [1] Executing tool: calculator(...)
[2026-06-07 ...] [TOOL] calculator → (15+27)*3 - 8^2 = 62
[2026-06-07 ...] [STEP] [2] Calling LLM...
[2026-06-07 ...] [STEP] [2] Final answer received (finish=stop)

🤖 Agent > 计算结果为 62
```

**口播讲解**：
> "可以看到日志中完整记录了：LLM 判断需要调用 calculator 工具、工具参数、工具执行结果、以及最终的文本回答。整个决策循环由 Agent 核心自己控制。"

**操作 2 — 搜索工具调用**：
```
🧑 You > 搜索一下 Java 的最新特性
```

**预期输出**：
```
[LLM] Tool call requested → search({"query":"Java"})
[TOOL] search → Search result for "Java": Java is a high-level...
```

**口播**：
> "搜索工具是一个 Mock 实现，基于关键词匹配返回预定义知识库内容。在实际项目中可以替换为真正的搜索 API。"

---

### 第三部分：跨轮延续演示（1.5 分钟）⭐ 核心

**画面**：输入 `:demo` 运行自动演示，或手动输入两轮对话

**口播**：
> "接下来是本次的重点——跨轮延续执行。这是题目明确要求的场景。"
> "演示分两轮。第一轮创建任务，第二轮追问进度。两轮共享同一个 Session。"

**操作（手动演示更真实）**：

**Turn 1**：
```
🧑 You > 帮我创建一个学习计划，包含3个任务：学习Java基础、学习Python数据分析、学习AI Agent开发。每个任务初始状态设为pending。
```

**预期输出**：
```
[STEP] [1] Calling LLM...
[LLM] Tool call requested → todo({"action":"add","title":"学习Java基础","status":"pending"})
[STEP] [1] Executing tool: todo(...)
[TOOL] todo → Task created: [id=a1b2c3d4] "学习Java基础" (status: pending)

[LLM] Tool call requested → todo({"action":"add","title":"学习Python数据分析",...})
[TOOL] todo → Task created: [id=e5f6g7h8] "学习Python数据分析" (status: pending)

[LLM] Tool call requested → todo({"action":"add","title":"学习AI Agent开发",...})
[TOOL] todo → Task created: [id=i9j0k1l2] "学习AI Agent开发" (status: pending)

[STEP] [4] Final answer received

🤖 Agent > 已为您创建了3个学习任务：
1. [a1b2c3d4] 学习Java基础 — pending
2. [e5f6g7h8] 学习Python数据分析 — pending
3. [i9j0k1l2] 学习AI Agent开发 — pending
```

**口播**：
> "第一轮调用了 3 次 todo.add，任务数据被写入了 session.state 中。
> 注意——Agent 在一次 user 输入中自动进行了多次工具调用，这是 LLM 自己决策的。"

**Turn 2（关键）**：
```
🧑 You > 第一个任务学习Java的进度如何？请帮我把它的状态标记为in_progress，然后列出所有任务。
```

**预期输出**：
```
[LLM] Tool call requested → todo({"action":"list"})
[STEP] [1] Executing tool: todo({"action":"list"})
[TOOL] todo → Tasks (3 of 3 total):
  1. [a1b2c3d4] 学习Java基础 — pending
  2. [e5f6g7h8] 学习Python数据分析 — pending
  3. [i9j0k1l2] 学习AI Agent开发 — pending

[LLM] Tool call requested → todo({"action":"update","id":"a1b2c3d4","status":"in_progress"})
[STEP] [2] Executing tool: todo(update...)
[TOOL] todo → Task updated: [id=a1b2c3d4] "学习Java基础" → status=in_progress

[LLM] Tool call requested → todo({"action":"list"})
[STEP] [3] Executing tool: todo(list)
[TOOL] todo → Tasks (3 of 3 total):
  1. [a1b2c3d4] 学习Java基础 — in_progress  ← 状态已变
  2. [e5f6g7h8] 学习Python数据分析 — pending
  3. [i9j0k1l2] 学习AI Agent开发 — pending

🤖 Agent > 第一个任务"学习Java基础"当前进度已完成标记。
整体状态：1/3 进行中，2/3 待开始。
```

**口播（重点讲解）**：
> "这是跨轮延续的关键——Turn 2 中 todo.list 读取到的 3 个任务，正是 Turn 1 创建的。
> 它们存储在同一个 Session 对象的 state Map 中，跨轮不丢失。
> Agent 不是把每一轮当成全新问题，而是能基于已有状态继续处理。"

---

### 第四部分：Session 管理演示（30 秒）

**操作**：
```
:sessions
```

**预期输出**：
```
Active sessions:
  a1b2c3d4 | messages=12 | state keys=[todo_tasks] ← current
```

**口播**：
> "可以看到当前 session 有 12 条消息，state 中存有 todo_tasks。
> 这就是跨轮状态持久化的数据。可以 :new 创建新 session，也可以 :session <id> 切换回去。"

---

### 第五部分：错误处理演示（30 秒）

**操作**：
```
🧑 You > 帮我计算 abc / 0
```

**预期输出**：
```
[TOOL] calculator → Error: Expression contains invalid characters...
或
[TOOL] calculator → Error: Failed to evaluate expression...
```

**口播**：
> "当工具执行遇到错误时，异常会被 catch 并转为错误信息返回给 LLM，
> LLM 感知到错误后会向用户解释问题，而不是让程序崩溃。"

---

### 第六部分：总结（30 秒）

**画面**：切回项目文件树或架构图

**口播**：
> "总结一下这个项目。从架构到 runtime 全部自实现：
> - Agent.java 实现了完整的决策循环，包含步数限制和异常处理
> - 3 个工具覆盖了计算、搜索、状态管理三种典型场景
> - Session.state 实现了跨轮状态延续
> - ExecutionLogger 提供完整的工具调用 trace
> - 只用了一个外部依赖 Jackson，HTTP 请求用 Java 内置 HttpClient
>
> 代码链接见项目 README，完整的 AI 开发记录见 AI_DEVELOPMENT_LOG.md。谢谢！"

---

## 录屏工具建议

| 工具 | 平台 | 特点 |
|------|------|------|
| OBS Studio | 全平台 | 免费，功能强大 |
| Windows 自带录屏 | Windows | Win+Alt+R，简单快捷 |
| Kap | macOS | 轻量，支持 GIF |
| Loom | 全平台 | 可分享链接 |

## 注意事项

1. **提前设置好环境变量**，避免录屏时暴露 API Key（可临时 export）
2. **终端字体调大**，确保录屏清晰
3. **关闭无关窗口和通知**，保持画面整洁
4. **语速适中**，关键概念（跨轮延续）停顿 1-2 秒
5. **录完后检查音频**，确认声音清晰
