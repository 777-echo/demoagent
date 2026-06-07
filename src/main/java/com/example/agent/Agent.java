package com.example.agent;

import com.example.llm.LLMClient;
import com.example.llm.LLMClient.LLMResponse;
import com.example.llm.LLMClient.ToolCall;
import com.example.log.ExecutionLogger;
import com.example.tool.Tool;
import com.example.tool.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The core Agent -- implements the full decision loop:
 *
 *   1. Receive user input
 *   2. Ask LLM: answer directly or call tools?
 *   3. If tool calls -> execute tools -> feed results back
 *   4. Repeat until final answer or max steps reached
 *
 * Supports multi-turn conversation via Session, and cross-turn
 * continuation via session state persistence.
 */
public class Agent {

    private final LLMClient llmClient;
    private final Map<String, Tool> tools;
    private final AgentConfig config;
    private final ExecutionLogger log;
    private final ObjectMapper mapper;

    public Agent(LLMClient llmClient, List<Tool> toolList, AgentConfig config) {
        this.llmClient = llmClient;
        this.config = config;
        this.log = ExecutionLogger.get();
        this.mapper = new ObjectMapper();

        this.tools = new HashMap<>();
        for (Tool t : toolList) {
            this.tools.put(t.name(), t);
        }
    }

    /**
     * Process a user message within a session. Returns the agent's final response.
     *
     * @param userInput the user's message
     * @param session   the conversation session (carries history + state)
     * @return the agent's text response
     */
    public String run(String userInput, Session session) {
        log.separator();
        log.info("Session: " + session.getSessionId() + " | User: " + truncate(userInput, 100));

        // Add user message to session history
        session.addUserMessage(userInput);

        // Build the full message list for the LLM:
        // [system prompt] + [conversation history...] + [latest user message]
        List<Map<String, Object>> messages = buildMessages(session);
        List<Map<String, Object>> toolDefs = buildToolDefinitions();

        int step = 0;
        StringBuilder finalAnswer = new StringBuilder();

        while (step < config.maxSteps()) {
            log.step(step + 1, "Calling LLM...");

            try {
                LLMResponse response = llmClient.chat(messages, toolDefs);

                // Case 1: LLM returned a text answer (final)
                if (!response.hasToolCalls() && response.content() != null) {
                    session.addAssistantMessage(response.content());
                    finalAnswer.append(response.content());
                    log.step(step + 1, "Final answer received (finish=" + response.finishReason() + ")");
                    break;
                }

                // Case 2: LLM returned tool calls
                if (response.hasToolCalls()) {
                    // Add assistant message with tool calls to history
                    List<Map<String, Object>> toolCallsRaw = convertToolCalls(response.toolCalls());
                    session.addAssistantToolCalls(toolCallsRaw);
                    messages.add(buildAssistantToolCallMessage(toolCallsRaw));

                    // Execute each tool call
                    for (ToolCall tc : response.toolCalls()) {
                        log.step(step + 1, "Executing tool: " + tc.name() + "(" + truncate(tc.arguments(), 80) + ")");

                        Tool tool = tools.get(tc.name());
                        String resultContent;

                        if (tool == null) {
                            resultContent = "Error: Unknown tool '" + tc.name() + "'. Available tools: " + tools.keySet();
                            log.error("Unknown tool: " + tc.name());
                        } else {
                            try {
                                // Parse arguments from JSON
                                Map<String, Object> args = parseArgs(tc.arguments());
                                // Execute the tool with session state
                                ToolResult result = tool.execute(args, session.getState());
                                resultContent = result.toContent();
                                log.tool(tc.name() + " -> " + truncate(resultContent, 150));
                            } catch (Exception e) {
                                resultContent = "Error executing tool '" + tc.name() + "': " + e.getMessage();
                                log.error("Tool execution failed: " + tc.name() + " -- " + e.getMessage());
                            }
                        }

                        // Add tool result to session and messages
                        session.addToolResult(tc.id(), tc.name(), resultContent);
                        messages.add(buildToolResultMessage(tc.id(), tc.name(), resultContent));
                    }

                    step++;
                    continue;
                }

                // Case 3: No content and no tool calls -- unusual, treat as error
                log.error("LLM returned empty response (finish=" + response.finishReason() + ")");
                finalAnswer.append("(Agent received an empty response from the LLM)");
                break;

            } catch (Exception e) {
                log.error("Agent loop error at step " + (step + 1) + ": " + e.getMessage());
                finalAnswer.append("Sorry, an error occurred: ").append(e.getMessage());
                break;
            }
        }

        // Max steps reached -- force a summary
        if (step >= config.maxSteps() && finalAnswer.isEmpty()) {
            log.info("Max steps (" + config.maxSteps() + ") reached -- requesting forced summary");
            session.addUserMessage("You have reached the maximum number of steps. Please summarize what you know so far based on the tool results you've received, and give the best answer you can.");
            messages = buildMessages(session);
            try {
                LLMResponse response = llmClient.chat(messages, List.of()); // no tools, force text
                if (response.content() != null) {
                    session.addAssistantMessage(response.content());
                    finalAnswer.append(response.content());
                }
            } catch (Exception e) {
                finalAnswer.append("(Max steps reached and forced summary failed: ").append(e.getMessage()).append(")");
            }
        }

        log.separator();
        return finalAnswer.toString();
    }

    // --- Message building ---

    private List<Map<String, Object>> buildMessages(Session session) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System prompt
        messages.add(Map.of("role", "system", "content", config.systemPrompt()));

        // Conversation history
        messages.addAll(session.getMessages());

        return messages;
    }

    private Map<String, Object> buildAssistantToolCallMessage(List<Map<String, Object>> toolCalls) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "assistant");
        msg.put("content", (String) null);
        msg.put("tool_calls", toolCalls);
        return msg;
    }

    private Map<String, Object> buildToolResultMessage(String toolCallId, String toolName, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", toolCallId);
        msg.put("name", toolName);
        msg.put("content", content);
        return msg;
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (Tool t : tools.values()) {
            Map<String, Object> def = new HashMap<>();
            def.put("type", "function");
            Map<String, Object> func = new HashMap<>();
            func.put("name", t.name());
            func.put("description", t.description());
            func.put("parameters", t.parameters());
            def.put("function", func);
            defs.add(def);
        }
        return defs;
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String jsonArgs) {
        try {
            if (jsonArgs == null || jsonArgs.isBlank()) {
                return Map.of();
            }
            return mapper.readValue(jsonArgs, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse tool arguments JSON: " + jsonArgs);
            return Map.of();
        }
    }

    private List<Map<String, Object>> convertToolCalls(List<ToolCall> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolCall tc : toolCalls) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", tc.id());
            entry.put("type", "function");
            Map<String, Object> func = new HashMap<>();
            func.put("name", tc.name());
            func.put("arguments", tc.arguments());
            entry.put("function", func);
            result.add(entry);
        }
        return result;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
