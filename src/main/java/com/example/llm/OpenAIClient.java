package com.example.llm;

import com.example.agent.AgentConfig;
import com.example.log.ExecutionLogger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible API client using Java's built-in HttpClient.
 *
 * Compatible with OpenAI, DeepSeek, Qwen, and any other service
 * that implements the /v1/chat/completions endpoint.
 */
public class OpenAIClient implements LLMClient {

    private final AgentConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final ExecutionLogger log;

    public OpenAIClient(AgentConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
        this.log = ExecutionLogger.get();
    }

    @Override
    public LLMResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        try {
            Map<String, Object> requestBody = buildRequestBody(messages, tools);
            String json = mapper.writeValueAsString(requestBody);

            log.llm("Sending request -> model=" + config.model() + ", messages=" + messages.size());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("API error: HTTP " + response.statusCode() + " -> " + response.body());
                throw new RuntimeException("LLM API error: HTTP " + response.statusCode() + " -- " + response.body());
            }

            return parseResponse(response.body());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM call failed: " + e.getMessage());
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    // --- Request building ---

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        body.put("temperature", 0.3);
        return body;
    }

    // --- Response parsing ---

    @SuppressWarnings("unchecked")
    private LLMResponse parseResponse(String responseBody) throws Exception {
        Map<String, Object> root = mapper.readValue(responseBody, new TypeReference<>() {});

        List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM response has no choices");
        }

        Map<String, Object> choice = choices.get(0);
        String finishReason = (String) choice.get("finish_reason");
        Map<String, Object> message = (Map<String, Object>) choice.get("message");

        String content = (String) message.get("content");
        List<Map<String, Object>> toolCallsRaw = (List<Map<String, Object>>) message.get("tool_calls");

        // Parse tool calls
        List<ToolCall> toolCalls = null;
        if (toolCallsRaw != null && !toolCallsRaw.isEmpty()) {
            toolCalls = new ArrayList<>();
            for (Map<String, Object> tc : toolCallsRaw) {
                String id = (String) tc.get("id");
                Map<String, Object> function = (Map<String, Object>) tc.get("function");
                String name = (String) function.get("name");
                String arguments = (String) function.get("arguments");
                toolCalls.add(new ToolCall(id, name, arguments));
                log.llm("Tool call requested -> " + name + "(" + arguments + ")");
            }
        }

        if (content != null && !content.isBlank()) {
            log.llm("Response text: " + truncate(content, 200));
        }

        return new LLMResponse(content, toolCalls, finishReason);
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
