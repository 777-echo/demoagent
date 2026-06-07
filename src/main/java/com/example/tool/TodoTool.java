package com.example.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Task/todo management tool -- the key enabler for cross-turn continuation.
 *
 * Tasks are stored in the session's state map, so they persist across
 * multiple user turns within the same session. When a user creates a task
 * in turn 1 and asks about progress in turn 2, the agent can read the
 * stored state and respond appropriately.
 *
 * Actions: add, list, update, delete
 */
public class TodoTool implements Tool {

    // The key used in sessionState to store the task list
    private static final String STATE_KEY = "todo_tasks";

    @Override
    public String name() { return "todo"; }

    @Override
    public String description() {
        return """
            Manage a task list that persists across conversation turns.
            Actions:
            - add: Create a new task. Requires "title" (string).
            - list: List all tasks. Optional "status" filter (pending/in_progress/done).
            - update: Update a task. Requires "id" (string). Optional "status" and "title".
            - delete: Delete a task. Requires "id" (string).
            Use this to create, track, and manage tasks for the user.""";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "description", "The action to perform: add, list, update, or delete"
                ),
                "id", Map.of(
                    "type", "string",
                    "description", "Task ID (required for update and delete)"
                ),
                "title", Map.of(
                    "type", "string",
                    "description", "Task title (required for add, optional for update)"
                ),
                "status", Map.of(
                    "type", "string",
                    "description", "Task status: pending, in_progress, or done (optional for add and update)"
                )
            ),
            "required", java.util.List.of("action")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> args, Map<String, Object> sessionState) {
        String action = (String) args.get("action");
        if (action == null || action.isBlank()) {
            return ToolResult.error("Missing 'action' argument. Must be one of: add, list, update, delete");
        }

        // Get or create the task list from session state
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) sessionState.get(STATE_KEY);
        if (tasks == null) {
            tasks = new ArrayList<>();
            sessionState.put(STATE_KEY, tasks);
        }

        return switch (action.toLowerCase()) {
            case "add" -> doAdd(args, tasks);
            case "list" -> doList(args, tasks);
            case "update" -> doUpdate(args, tasks);
            case "delete" -> doDelete(args, tasks);
            default -> ToolResult.error("Unknown action: '" + action + "'. Must be one of: add, list, update, delete");
        };
    }

    // --- Action implementations ---

    private ToolResult doAdd(Map<String, Object> args, List<Map<String, Object>> tasks) {
        String title = (String) args.get("title");
        if (title == null || title.isBlank()) {
            return ToolResult.error("'title' is required for add action");
        }

        String status = getStringArg(args, "status", "pending");
        if (!isValidStatus(status)) {
            return ToolResult.error("Invalid status: '" + status + "'. Must be: pending, in_progress, done");
        }

        Map<String, Object> task = new HashMap<>();
        String id = UUID.randomUUID().toString().substring(0, 8);
        task.put("id", id);
        task.put("title", title);
        task.put("status", status);
        task.put("createdAt", Instant.now().toString());
        tasks.add(task);

        return ToolResult.success("Task created: [id=" + id + "] \"" + title + "\" (status: " + status + ")");
    }

    private ToolResult doList(Map<String, Object> args, List<Map<String, Object>> tasks) {
        if (tasks.isEmpty()) {
            return ToolResult.success("No tasks found. Create one with action=add.");
        }

        String statusFilter = getStringArg(args, "status", null);
        List<Map<String, Object>> filtered = tasks;
        if (statusFilter != null && !statusFilter.isBlank()) {
            filtered = tasks.stream()
                .filter(t -> statusFilter.equalsIgnoreCase((String) t.get("status")))
                .collect(Collectors.toList());
        }

        if (filtered.isEmpty()) {
            return ToolResult.success("No tasks matching filter status='" + statusFilter + "'.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Tasks (").append(filtered.size()).append(" of ").append(tasks.size()).append(" total):\n");
        for (int i = 0; i < filtered.size(); i++) {
            Map<String, Object> t = filtered.get(i);
            sb.append("  ").append(i + 1).append(". [").append(t.get("id")).append("] ")
                .append(t.get("title")).append(" -- ").append(t.get("status"));
            if (i < filtered.size() - 1) sb.append("\n");
        }
        return ToolResult.success(sb.toString());
    }

    private ToolResult doUpdate(Map<String, Object> args, List<Map<String, Object>> tasks) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            return ToolResult.error("'id' is required for update action");
        }

        Map<String, Object> target = null;
        for (Map<String, Object> t : tasks) {
            if (id.equals(t.get("id"))) {
                target = t;
                break;
            }
        }

        if (target == null) {
            return ToolResult.error("Task not found: id=" + id + ". Use list to see available tasks.");
        }

        String newTitle = getStringArg(args, "title", null);
        String newStatus = getStringArg(args, "status", null);

        if (newTitle != null && !newTitle.isBlank()) {
            target.put("title", newTitle);
        }
        if (newStatus != null && !newStatus.isBlank()) {
            if (!isValidStatus(newStatus)) {
                return ToolResult.error("Invalid status: '" + newStatus + "'. Must be: pending, in_progress, done");
            }
            target.put("status", newStatus);
        }

        return ToolResult.success("Task updated: [id=" + id + "] \"" + target.get("title") + "\" -> status=" + target.get("status"));
    }

    private ToolResult doDelete(Map<String, Object> args, List<Map<String, Object>> tasks) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            return ToolResult.error("'id' is required for delete action");
        }

        boolean removed = tasks.removeIf(t -> id.equals(t.get("id")));
        if (!removed) {
            return ToolResult.error("Task not found: id=" + id + ". Use list to see available tasks.");
        }

        return ToolResult.success("Task deleted: id=" + id);
    }

    // --- Helpers ---

    private String getStringArg(Map<String, Object> args, String key, String defaultVal) {
        Object val = args.get(key);
        if (val instanceof String s && !s.isBlank()) {
            return s;
        }
        return defaultVal;
    }

    private boolean isValidStatus(String status) {
        return "pending".equals(status) || "in_progress".equals(status) || "done".equals(status);
    }
}
