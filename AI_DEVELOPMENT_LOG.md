# AI Prompt 与问题解决记录

## 项目概述
从零构建一个最小可用的 AI Agent，使用 Java 23 + Maven，核心 runtime 自实现，
支持多轮对话、工具调用、Session 管理、跨轮状态延续。

AI 工具：Claude Code（模型 deepseek-v4-pro）

---

## 阶段一：需求分析与方案设计

### Prompt 1
```
笔试题：从零实现一个最小可用 Agent
允许使用任何 AI 工具辅助开发，从零实现一个最小可用的 Agent。
要求：
- 支持多轮对话和 session 维护
- 不直接依赖现成 Agent 框架完成主流程（如 LangChain / OpenHands 等），核心 runtime 需要自己实现
- Agent 至少支持一个基本循环：接收用户输入 → 判断是直接回答，还是调用工具 → 执行工具 → 读取工具结果 → 继续下一步，直到给出最终答案
- 至少提供 3 个工具，例如：calculator、search（可 mock）、read_docs / todo / weather（可自定义）
- 需要有：最大步数限制、基本异常处理、工具调用 trace 或执行日志
- 至少支持一个"跨轮次继续执行"的场景
- 需要使用真实的 LLM API
提交内容：
- 代码链接
- 终端或网页操作录屏
- README（运行方式、系统设计、memory 的召回时机与放置方式说明）
- AI Prompt 与问题解决记录

怎么实现 帮我制定路线
```

### AI 回答要点
- 识别出这是一个 Maven Java 23 项目（从 pom.xml 读取）
- 建议包结构：agent / tool / llm / log 四个包
- 设计了 Agent 循环状态机：文本回答=终态，tool_calls=中间态
- 跨轮延续方案：Session.state Map 存储工具数据
- 建议 3 个工具：Calculator（自实现解析器）、Search（Mock 知识库）、Todo（CRUD + 状态持久化）
- LLM 客户端：用 OpenAI 兼容协议（国内服务通用）
- 最小依赖：只用 Jackson，HTTP 用 Java 内置 HttpClient

### 解决的问题
- **题目拆解**：把 10+ 条需求映射到具体类和接口
- **跨轮延续设计**：确定了 `session.state` 可变 Map 的方案（而非外部数据库）
- **工具选择**：Todo 既能做 CRUD 又能演示跨轮，一举两得

---

## 阶段二：编码实现（自底向上）

### Prompt 2（进入 Plan Mode 后）
```
（系统自动将计划写入 plan file，用户 approve 后开始实现）
```

### 实现步骤

#### 第 1 批：基础设施（5 个文件并行写入）
- `AgentConfig.java` — 环境变量读取
- `Session.java` — 消息历史 + 状态 Map
- `Tool.java` — 工具接口
- `ToolResult.java` — 工具结果 POJO
- `ExecutionLogger.java` — 单例日志

**无问题，直接通过。**

#### 第 2 批：LLM 客户端 + 3 个工具（5 个文件并行写入）
- `LLMClient.java` — 接口 + record 定义
- `OpenAIClient.java` — OpenAI 兼容实现
- `CalculatorTool.java` — 自实现递归下降解析器
- `SearchTool.java` — Mock 知识库匹配
- `TodoTool.java` — CRUD + session state 操作

**无问题，直接通过。**

#### 第 3 批：Agent 核心循环 + CLI 入口
- `Agent.java` — while(step < maxSteps) 循环
- `Main.java` — 交互式 CLI + :demo 演示

**无问题，直接通过。**

---

## 阶段三：编译与问题修复

### 编译命令
```bash
mvn compile
```

### 问题 1：CalculatorTool 编译错误

**错误信息**：
```
CalculatorTool.java:[67,26] 变量 expr 未在默认构造器中初始化
```

**原因分析**：
最初把递归下降解析器的状态变量（`expr`, `pos`）作为 CalculatorTool 的字段，
后来重构为内部类 `Parser` 时，Parser 有了自己的 `expr` 和 `pos`，
但外层 CalculatorTool 的两个字段忘记删除。

**AI Prompt 3**：
```
（编译报错后，AI 自动 Read CalculatorTool.java 第 60-75 行发现问题）
```

**解决方案**：
删除 CalculatorTool 中的残留字段：
```java
// 删除这两行
private final String expr;
private int pos;
```
保留 `evaluate()` 方法，它内部 new Parser(expression) 创建解析器实例。

**修复方式**：
```
Edit 工具精确替换：old_string 包含残留字段，new_string 不含
```

**经验教训**：
重构时把逻辑从外层类搬到内部类后，要检查外层类是否还有残留的状态变量。
这是常见的重构疏忽。

### 编译结果
```
BUILD SUCCESS — 12 source files compiled, 0 errors, 1 warning (Java version target hint)
```

---

## 阶段四：README 与文档生成

### AI Prompt 4
```
（AI 自动生成 README.md，包含架构图、运行方式、跨轮数据流、执行日志示例）
```

**无问题，直接通过。**

---

## 阶段五：补充提交材料

### AI Prompt 5
```
根据笔试要求 判断需要提交什么
```

### AI 回答要点
- 已完成的：代码链接、README
- 缺失的：录屏、AI Prompt 与问题解决记录
- 建议创建 `AI_DEVELOPMENT_LOG.md` 和录屏演示脚本

### AI Prompt 6
```
都做
```

### AI 回答
- 生成 AI_DEVELOPMENT_LOG.md（本文档）
- 生成 SCREENCAST_SCRIPT.md（录屏演示脚本）

---

## 总结：AI 辅助开发的价值

| 环节 | AI 的作用 | 人工介入 |
|------|----------|---------|
| 需求分析 & 架构设计 | 拆解需求→映射到类/接口→输出完整架构方案 | 确认方案合理性，Approve Plan |
| 代码实现 | 12 个 Java 文件全部由 AI 生成 | 审查代码逻辑 |
| 编译调试 | 读取错误信息→定位问题→自动修复 | 触发编译命令 |
| 文档生成 | README、开发日志、录屏脚本 | 确认内容完整性 |

### 遇到的唯一技术问题

**编译错误**：CalculatorTool 重构后残留字段。
- 发现方式：`mvn compile` 报错
- 定位方式：AI 读取报错行附近的代码
- 修复方式：精确文本替换，删除 2 行残留代码
- 修复耗时：约 30 秒

### 设计决策记录

1. **为什么用 OpenAI 协议而非 Anthropic？**
   → 国内 LLM 服务（DeepSeek、通义千问）普遍兼容 OpenAI 格式，通用性更好。

2. **为什么只用 Jackson 一个外部依赖？**
   → 展示"从零实现"的诚意；Java 内置 HttpClient 已足够；面试官会看重轻量级设计。

3. **为什么计算器自实现解析器而非用 ScriptEngine？**
   → ScriptEngine 可执行任意代码，不安全；自实现递归下降能展示编码功底；可精确定义允许的语法。

4. **为什么选择 Todo 作为第三个工具？**
   → 既能演示 CRUD，又能演示跨轮延续，一举两得。Search + Calculator + Todo 覆盖了"获取信息 + 计算 + 状态管理"三种典型工具模式。
