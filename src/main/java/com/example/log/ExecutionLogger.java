package com.example.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple singleton logger for agent execution trace.
 *
 * Outputs timestamped, leveled logs to stdout so the user can follow
 * what the agent is doing step-by-step.
 */
public class ExecutionLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static ExecutionLogger instance;

    private ExecutionLogger() {}

    public static synchronized ExecutionLogger get() {
        if (instance == null) {
            instance = new ExecutionLogger();
        }
        return instance;
    }

    // --- Public logging methods ---

    public void step(int stepNum, String message) {
        log("STEP", "[" + stepNum + "] " + message);
    }

    public void llm(String message) {
        log("LLM", message);
    }

    public void tool(String message) {
        log("TOOL", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void separator() {
        System.out.println("=".repeat(60));
    }

    // --- Internal ---

    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(FMT);
        System.out.println("[" + timestamp + "] [" + level + "] " + message);
    }
}
