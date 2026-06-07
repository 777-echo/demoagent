package com.example.tool;

/**
 * Result of a tool execution.
 */
public class ToolResult {

    private final boolean success;
    private final String data;
    private final String error;

    private ToolResult(boolean success, String data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    /** Create a successful result. */
    public static ToolResult success(String data) {
        return new ToolResult(true, data, null);
    }

    /** Create an error result. */
    public static ToolResult error(String error) {
        return new ToolResult(false, null, error);
    }

    /** Get the content to send back to the LLM. */
    public String toContent() {
        if (success) {
            return data;
        } else {
            return "Error: " + error;
        }
    }

    public boolean isSuccess() { return success; }
    public String getData() { return data; }
    public String getError() { return error; }
}
