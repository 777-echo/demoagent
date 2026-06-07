package com.example.tool;

import java.util.Map;

/**
 * Interface for Agent tools.
 *
 * Each tool must provide its name, description, and JSON Schema parameters
 * so the LLM can understand when and how to use it.
 *
 * The {@code execute} method receives the arguments from the LLM and the
 * session state map for cross-turn data persistence.
 */
public interface Tool {

    /** Unique tool name (used by LLM in function.name). */
    String name();

    /** Human-readable description of what the tool does. */
    String description();

    /** JSON Schema for the tool's parameters (as a Map that serializes to JSON). */
    Map<String, Object> parameters();

    /**
     * Execute the tool with the given arguments.
     *
     * @param args         arguments from the LLM (parsed from JSON)
     * @param sessionState mutable session state for cross-turn persistence
     * @return the execution result
     */
    ToolResult execute(Map<String, Object> args, Map<String, Object> sessionState);
}
