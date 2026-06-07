package com.example;

import com.example.agent.Agent;
import com.example.agent.AgentConfig;
import com.example.agent.Session;
import com.example.llm.OpenAIClient;
import com.example.log.ExecutionLogger;
import com.example.tool.CalculatorTool;
import com.example.tool.SearchTool;
import com.example.tool.TodoTool;
import com.example.tool.Tool;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CLI entry point for the Minimal Agent.
 *
 * Supports:
 *   - Interactive multi-turn conversation
 *   - Multiple sessions (switch with ":session <id>")
 *   - Cross-turn continuation demo (":demo")
 *   - Built-in commands: :help, :sessions, :new, :demo, :quit
 */
public class Main {

    private static final ExecutionLogger log = ExecutionLogger.get();

    // Session storage (static so --once mode can persist across calls)
    private static final Map<String, Session> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private static Session currentSession;

    public static void main(String[] args) {
        // Force UTF-8 output on Windows terminals
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        // Initialize configuration (reads from environment variables)
        AgentConfig config;
        try {
            config = new AgentConfig();
        } catch (Exception e) {
            System.err.println("[ERROR] Configuration error: " + e.getMessage());
            System.err.println();
            System.err.println("Please set the required environment variables:");
            System.err.println("  export OPENAI_API_KEY=your_api_key");
            System.err.println("  export OPENAI_BASE_URL=https://api.openai.com/v1   (optional)");
            System.err.println("  export LLM_MODEL=gpt-4o                             (optional)");
            System.err.println("  export MAX_STEPS=10                                 (optional)");
            System.exit(1);
            return;
        }

        // Initialize tools
        List<Tool> tools = List.of(
            new CalculatorTool(),
            new SearchTool(),
            new TodoTool()
        );

        // Initialize LLM client and agent
        OpenAIClient llmClient = new OpenAIClient(config);
        Agent agent = new Agent(llmClient, tools, config);

        // Get or create session
        String envSessionId = System.getenv("AGENT_SESSION_ID");

        // --once mode: single question, print answer, exit (with file persistence)
        if (args.length >= 1 && "--once".equals(args[0])) {
            if (args.length < 2) {
                System.err.println("Usage: mvn exec:java -Dexec.args=\"--once <question>\"");
                System.exit(1);
            }
            // Join all remaining args (supports multi-word questions)
            String question = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

            // Load session from disk if AGENT_SESSION_ID is set and file exists
            if (envSessionId != null) {
                Path sessionFile = Session.sessionFile(envSessionId);
                if (Files.exists(sessionFile)) {
                    try {
                        currentSession = Session.loadFromFile(sessionFile);
                        log.info("Session loaded from disk: " + envSessionId);
                    } catch (Exception e) {
                        log.error("Failed to load session: " + e.getMessage());
                        currentSession = new Session();
                    }
                } else {
                    currentSession = new Session();
                }
            } else {
                currentSession = new Session();
            }

            log.info("Model: " + config.model() + " | Tools: " + tools.stream().map(Tool::name).toList());
            log.info("Session: " + currentSession.getSessionId());

            String answer = agent.run(question, currentSession);
            System.out.println(answer);
            System.out.println();

            // Save session to disk for cross-process reuse
            String saveKey = envSessionId != null ? envSessionId : currentSession.getSessionId();
            try {
                Path sessionFile = Session.sessionFile(saveKey);
                Files.createDirectories(sessionFile.getParent());
                currentSession.saveToFile(sessionFile);
                log.info("Session saved to: " + sessionFile);
            } catch (Exception e) {
                log.error("Failed to save session: " + e.getMessage());
            }

            System.out.println("Session ID: " + saveKey);
            System.out.println("(Reuse with: export AGENT_SESSION_ID=" + saveKey + ")");
            return;
        }

        // Interactive mode -- in-memory session map
        if (envSessionId != null && sessions.containsKey(envSessionId)) {
            currentSession = sessions.get(envSessionId);
        } else {
            currentSession = new Session();
            sessions.put(currentSession.getSessionId(), currentSession);
            if (envSessionId != null) {
                sessions.put(envSessionId, currentSession);
            }
        }

        printBanner();
        log.info("Configuration loaded: model=" + config.model() + ", maxSteps=" + config.maxSteps());
        log.info("Tools registered: " + tools.stream().map(Tool::name).toList());
        log.info("Session created: " + currentSession.getSessionId());
        log.info("Type :help for commands, :demo for cross-turn demo, :quit to exit");
        log.separator();

        // --- CLI loop ---
        // Read raw bytes to handle GBK/UTF-8 mismatch on Chinese Windows
        java.io.InputStream rawIn = System.in;

        while (true) {
            System.out.print("\n[You] ");
            String input = readLineRobust(rawIn);
            if (input == null) break; // EOF
            input = input.trim();

            if (input.isEmpty()) continue;

            // Built-in commands
            if (input.startsWith(":")) {
                String result = handleCommand(input, sessions, currentSession);
                if (result == null) break; // :quit
                if (result.equals("__SWITCH__")) {
                    String sid = input.substring(9).trim();
                    currentSession = sessions.get(sid);
                    if (currentSession == null) {
                        currentSession = new Session();
                        sessions.put(currentSession.getSessionId(), currentSession);
                        log.info("Session not found, created new: " + currentSession.getSessionId());
                    }
                } else if (result.equals("__NEW__")) {
                    currentSession = new Session();
                    sessions.put(currentSession.getSessionId(), currentSession);
                    log.info("New session created: " + currentSession.getSessionId());
                } else if (result.equals("__DEMO__")) {
                    runCrossTurnDemo(agent, currentSession);
                } else if (!result.isEmpty()) {
                    System.out.println(result);
                }
                continue;
            }

            // Process user input through the agent
            try {
                System.out.print("\n[Agent] ");
                String response = agent.run(input, currentSession);
                System.out.println(response);
            } catch (Exception e) {
                log.error("Agent failed: " + e.getMessage());
                System.err.println("[ERROR] Agent error: " + e.getMessage());
                System.err.println("   You can continue chatting or type :quit to exit.");
            }
        }

        System.out.println("\nGoodbye!");
    }

    // --- Command handling ---

    /**
     * Handle a built-in command (prefixed with ":").
     * Returns null to signal quit, "__SWITCH__" for session switch, etc.
     */
    private static String handleCommand(String input, Map<String, Session> sessions, Session current) {
        String cmd = input.toLowerCase();

        return switch (cmd) {
            case ":quit", ":q" -> null;

            case ":help", ":h" -> """
                Commands:
                  :help, :h       Show this help
                  :sessions, :ss  List all sessions
                  :new, :n        Create a new session
                  :session <id>   Switch to a session by ID
                  :demo, :d       Run cross-turn continuation demo
                  :quit, :q       Exit
                """;

            case ":sessions", ":ss" -> {
                StringBuilder sb = new StringBuilder("Active sessions:\n");
                for (Session s : sessions.values()) {
                    String marker = s.getSessionId().equals(current.getSessionId()) ? " <- current" : "";
                    sb.append("  ").append(s.getSessionId())
                        .append(" | messages=").append(s.getMessages().size())
                        .append(" | state keys=").append(s.getState().keySet())
                        .append(marker).append("\n");
                }
                yield sb.toString();
            }

            case ":new", ":n" -> "__NEW__";

            case ":demo", ":d" -> "__DEMO__";

            default -> {
                if (cmd.startsWith(":session ") || cmd.startsWith(":s ")) {
                    yield "__SWITCH__";
                }
                yield "Unknown command: " + input + " -- type :help for available commands";
            }
        };
    }

    // --- Cross-turn continuation demo ---

    /**
     * Demonstrate the cross-turn continuation scenario:
     *
     * Turn 1: Create 3 learning tasks
     * Turn 2: Ask about progress and update a task status
     *
     * Both turns share the same session, so TodoTool state persists.
     */
    private static void runCrossTurnDemo(Agent agent, Session session) {
        log.separator();
        log.info("=== Cross-Turn Continuation Demo ===");
        log.info("Session: " + session.getSessionId());
        log.info("This demo shows 2 turns sharing the same session state.");
        log.separator();

        // Turn 1: Create tasks
        String turn1 = "请帮我创建一个学习计划，包含3个任务：学习Java基础、学习Python数据分析、学习AI Agent开发。每个任务初始状态为pending。";
        System.out.println("\n--- Turn 1 ---");
        System.out.println("[You] " + turn1);
        System.out.print("\n[Agent] ");
        String response1 = agent.run(turn1, session);
        System.out.println(response1);

        // Brief pause so user can read
        System.out.println("\n[Wait](Turn 1 done -- press Enter to continue to Turn 2...)");
        try {
            System.in.read();
        } catch (Exception ignored) {}

        // Turn 2: Check progress and update
        String turn2 = "第一个任务（学习Java基础）的进度如何？请帮我把它标记为in_progress，然后列出所有任务看看整体状态。";
        System.out.println("\n--- Turn 2 ---");
        System.out.println("[You]" + turn2);
        System.out.print("\n[Agent] ");
        String response2 = agent.run(turn2, session);
        System.out.println(response2);

        log.separator();
        log.info("=== Demo Complete ===");
        log.info("Notice how Turn 2 reads the tasks created in Turn 1 from the session state.");
        log.info("This is cross-turn continuation -- the agent is NOT starting fresh each time.");
        log.separator();
    }

    // --- Robust input reading (handles GBK/UTF-8 mismatch on Windows) ---

    /**
     * Read a line from stdin. We dump raw bytes, try all common encodings,
     * and use Console if available (handles encoding at OS level).
     */
    private static String readLineRobust(java.io.InputStream in) {
        try {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1 && b != '\n') {
                if (b != '\r') buf.write(b);
            }
            if (b == -1 && buf.size() == 0) return null;
            byte[] bytes = buf.toByteArray();

            // Try Console first (uses OS-level encoding, most reliable)
            java.io.Console c = System.console();
            // We can't use Console after reading raw bytes, so just try the decoders

            // Try UTF-8 (what chcp 65001 terminals actually send)
            String utf8 = new String(bytes, StandardCharsets.UTF_8);
            if (!utf8.contains("�")) return utf8;

            // Fallback: try GBK
            String gbk = new String(bytes, java.nio.charset.Charset.forName("GBK"));
            if (!gbk.contains("�")) return gbk;

            return utf8;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Quick check: does this string look like actual text (not total garbage)? */
    private static boolean looksLikeValidText(String s) {
        if (s.isEmpty()) return true;
        int printable = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7F) printable++;
        }
        return printable > s.length() / 2;
    }

    // --- Banner ---

    private static void printBanner() {
        System.out.println("================================================");
        System.out.println("  Minimal AI Agent -- From Scratch");
        System.out.println("  Multi-turn | Tool Calling | Cross-turn State");
        System.out.println("================================================");
    }
}
