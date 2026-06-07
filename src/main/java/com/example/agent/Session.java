package com.example.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A conversation session that holds the full message history and cross-turn persistent state.
 *
 * Each session is identified by a unique sessionId. The {@code state} map is where tools
 * (like TodoTool) persist data across multiple user turns -- this is what enables
 * "cross-turn continuation".
 *
 * Sessions can be saved to / loaded from JSON files for cross-process persistence.
 */
public class Session {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String sessionId;
    private List<Map<String, Object>> messages;
    private Map<String, Object> state;
    private long createdAtEpoch;
    private long lastAccessEpoch;

    public Session() {
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.messages = new ArrayList<>();
        this.state = new HashMap<>();
        Instant now = Instant.now();
        this.createdAtEpoch = now.toEpochMilli();
        this.lastAccessEpoch = now.toEpochMilli();
    }

    // --- Message management ---

    /** Add a message to the conversation history. */
    public void addMessage(Map<String, Object> message) {
        messages.add(message);
        touch();
    }

    /** Get all messages (including system prompt if added). */
    public List<Map<String, Object>> getMessages() {
        touch();
        return messages;
    }

    /** Convenience: add a simple user message. */
    public void addUserMessage(String content) {
        addMessage(Map.of("role", "user", "content", content));
    }

    /** Convenience: add a simple assistant message. */
    public void addAssistantMessage(String content) {
        addMessage(Map.of("role", "assistant", "content", content));
    }

    /** Convenience: add a system message. */
    public void addSystemMessage(String content) {
        addMessage(Map.of("role", "system", "content", content));
    }

    /** Add an assistant message that includes tool calls. */
    @SuppressWarnings("unchecked")
    public void addAssistantToolCalls(List<Map<String, Object>> toolCalls) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "assistant");
        msg.put("content", (String) null);
        msg.put("tool_calls", toolCalls);
        addMessage(msg);
    }

    /** Add a tool result message. */
    public void addToolResult(String toolCallId, String toolName, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", toolCallId);
        msg.put("name", toolName);
        msg.put("content", content);
        addMessage(msg);
    }

    // --- Session state (cross-turn persistence) ---

    /** Get the persistent state map. Tools use this to store data across turns. */
    public Map<String, Object> getState() {
        return state;
    }

    /** Convenience: get a typed value from session state. */
    @SuppressWarnings("unchecked")
    public <T> T getStateValue(String key, Class<T> type) {
        Object val = state.get(key);
        if (type.isInstance(val)) {
            return (T) val;
        }
        return null;
    }

    // --- Accessors ---

    public String getSessionId() { return sessionId; }
    @JsonIgnore public Instant getCreatedAt() { return Instant.ofEpochMilli(createdAtEpoch); }
    @JsonIgnore public Instant getLastAccessAt() { return Instant.ofEpochMilli(lastAccessEpoch); }

    private void touch() {
        this.lastAccessEpoch = Instant.now().toEpochMilli();
    }

    // --- File persistence (for cross-process session reuse) ---

    /** Save session to a JSON file. */
    public void saveToFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        MAPPER.writeValue(path.toFile(), this);
    }

    /** Load session from a JSON file. */
    public static Session loadFromFile(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), Session.class);
    }

    /** Default session storage directory. */
    public static Path sessionDir() {
        return Path.of(System.getProperty("java.io.tmpdir"), "agent-sessions");
    }

    /** Get file path for a given session key. */
    public static Path sessionFile(String sessionKey) {
        return sessionDir().resolve("session-" + sessionKey + ".json");
    }
}
