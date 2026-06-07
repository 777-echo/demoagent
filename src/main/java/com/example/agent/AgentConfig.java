package com.example.agent;

/**
 * Agent configuration -- loaded from environment variables with sensible defaults.
 *
 * Environment variables:
 *   OPENAI_API_KEY  -- required, the API key for LLM service
 *   OPENAI_BASE_URL -- optional, base URL for OpenAI-compatible API (default: https://api.openai.com/v1)
 *   LLM_MODEL       -- optional, model name (default: gpt-4o)
 *   MAX_STEPS       -- optional, max tool-calling steps per turn (default: 10)
 */
public class AgentConfig {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxSteps;
    private final String systemPrompt;

    public AgentConfig() {
        this.apiKey = envOrThrow("OPENAI_API_KEY");
        this.baseUrl = envOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1");
        this.model = envOrDefault("LLM_MODEL", "gpt-4o");
        this.maxSteps = Integer.parseInt(envOrDefault("MAX_STEPS", "10"));
        this.systemPrompt = buildSystemPrompt();
    }

    public AgentConfig(String apiKey, String baseUrl, String model, int maxSteps) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxSteps = maxSteps;
        this.systemPrompt = buildSystemPrompt();
    }

    private String buildSystemPrompt() {
        return """
            You are a helpful AI assistant with access to tools.
            Follow these rules strictly:
            1. When you can answer directly, do so without calling tools.
            2. When you need a tool, call it immediately -- do not ask the user for permission.
            3. After receiving tool results, use them to formulate your final answer.
            4. Do not call the same tool with the same arguments more than once.
            5. When the user asks about progress or status, use the todo tool to check existing tasks.
            6. Always respond in the same language the user used.
            7. NEVER use emoji or special Unicode symbols -- use plain text only.
            """;
    }

    // --- Getters ---

    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String model() { return model; }
    public int maxSteps() { return maxSteps; }
    public String systemPrompt() { return systemPrompt; }

    // --- Helpers ---

    private static String envOrThrow(String key) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException(
                "Missing required environment variable: " + key +
                "\nPlease set it, e.g.: export " + key + "=your_value"
            );
        }
        return val;
    }

    private static String envOrDefault(String key, String defaultVal) {
        String val = System.getenv(key);
        return (val == null || val.isBlank()) ? defaultVal : val;
    }
}
