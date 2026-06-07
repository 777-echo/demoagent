package com.example.llm;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for LLM API calls, decoupled from any specific provider.
 */
public interface LLMClient {

    /**
     * Send a chat completion request with tool definitions.
     *
     * @param messages the conversation history (OpenAI format)
     * @param tools    the available tool definitions (OpenAI format)
     * @return the LLM response containing either a text answer or tool calls
     */
    LLMResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

    /**
     * Response from the LLM -- either a direct text answer or a list of tool calls.
     */
    record LLMResponse(
        String content,                          // text answer (null if tool calls present)
        List<ToolCall> toolCalls,                // tool calls (null if text answer)
        String finishReason                      // "stop", "tool_calls", etc.
    ) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }

        public boolean isFinished() {
            return "stop".equals(finishReason);
        }
    }

    /** A single tool call from the LLM. */
    record ToolCall(
        String id,                               // unique call id
        String name,                             // function name
        String arguments                         // JSON string of arguments
    ) {}
}
