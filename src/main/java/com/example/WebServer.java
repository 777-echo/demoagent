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
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web UI for the Agent — serves a chat page at http://localhost:8080
 * Zero extra dependencies, uses Java built-in HttpServer.
 */
public class WebServer {

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static Agent agent;
    private static ExecutionLogger log;

    public static void main(String[] args) throws IOException {
        log = ExecutionLogger.get();

        // Init config and agent (same as CLI)
        AgentConfig config = new AgentConfig();
        List<Tool> tools = List.of(new CalculatorTool(), new SearchTool(), new TodoTool());
        agent = new Agent(new OpenAIClient(config), tools, config);

        // Start HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", WebServer::handlePage);
        server.createContext("/api/chat", WebServer::handleChat);
        server.setExecutor(null);
        server.start();

        log.info("Web UI started at http://localhost:8080");
        log.info("Open your browser and start chatting!");
    }

    /** Serve the HTML chat page. */
    private static void handlePage(HttpExchange exchange) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Minimal AI Agent</title>
            <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                   background: #1a1a2e; color: #eee; height: 100vh; display: flex; flex-direction: column; }
            header { background: #16213e; padding: 12px 20px; border-bottom: 1px solid #0f3460;
                     display: flex; justify-content: space-between; align-items: center; }
            header h1 { font-size: 18px; color: #e94560; }
            header span { font-size: 12px; color: #999; }
            #chat { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 10px; }
            .msg { max-width: 85%; padding: 10px 14px; border-radius: 12px; line-height: 1.5;
                   font-size: 14px; white-space: pre-wrap; word-break: break-word; }
            .msg.user { align-self: flex-end; background: #0f3460; color: #e0e0e0; }
            .msg.agent { align-self: flex-start; background: #16213e; color: #d0d0d0; border: 1px solid #0f3460; }
            .msg .label { font-size: 11px; color: #e94560; margin-bottom: 4px; font-weight: bold; }
            .msg.user .label { color: #53a8b6; }
            #input-area { display: flex; padding: 12px; background: #16213e; border-top: 1px solid #0f3460; }
            #input-area input { flex: 1; padding: 10px 14px; border: 1px solid #0f3460; border-radius: 20px;
                               background: #1a1a2e; color: #eee; font-size: 14px; outline: none; }
            #input-area input:focus { border-color: #e94560; }
            #input-area button { margin-left: 8px; padding: 10px 20px; border: none; border-radius: 20px;
                                 background: #e94560; color: white; font-size: 14px; cursor: pointer; }
            #input-area button:hover { background: #c23152; }
            #input-area button:disabled { background: #555; cursor: not-allowed; }
            .tool-log { font-size: 11px; color: #888; margin: 4px 0; font-style: italic; }
            #status { text-align: center; padding: 4px; font-size: 11px; color: #666; }
            </style>
            </head>
            <body>
            <header>
              <h1>Minimal AI Agent</h1>
              <span id="sessionInfo">Session: -</span>
            </header>
            <div id="chat"></div>
            <div id="status">Ready</div>
            <div id="input-area">
              <input id="userInput" type="text" placeholder="Type your message..."
                     autofocus onkeydown="if(event.key==='Enter')send()">
              <button id="sendBtn" onclick="send()">Send</button>
            </div>
            <script>
            let sessionId = localStorage.getItem('agentSessionId') || '';
            if (!sessionId) { sessionId = crypto.randomUUID().substring(0, 8); localStorage.setItem('agentSessionId', sessionId); }
            document.getElementById('sessionInfo').textContent = 'Session: ' + sessionId;

            async function send() {
              const input = document.getElementById('userInput');
              const msg = input.value.trim();
              if (!msg) return;
              input.value = '';
              input.disabled = true;
              document.getElementById('sendBtn').disabled = true;
              document.getElementById('status').textContent = 'Thinking...';

              addMessage('user', msg);
              try {
                const res = await fetch('/api/chat', {
                  method: 'POST',
                  headers: {'Content-Type': 'application/json'},
                  body: JSON.stringify({sessionId, message: msg})
                });
                const data = await res.json();
                addMessage('agent', data.response);
              } catch(e) {
                addMessage('agent', 'Error: ' + e.message);
              }
              input.disabled = false;
              document.getElementById('sendBtn').disabled = false;
              document.getElementById('status').textContent = 'Ready';
              input.focus();
            }

            function addMessage(role, text) {
              const div = document.createElement('div');
              div.className = 'msg ' + role;
              div.innerHTML = '<div class="label">' + (role==='user'?'You':'Agent') + '</div>' + text;
              document.getElementById('chat').appendChild(div);
              document.getElementById('chat').scrollTop = document.getElementById('chat').scrollHeight;
            }
            </script>
            </body>
            </html>
            """;

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Chat API: POST /api/chat  {sessionId, message} -> {response, sessionId} */
    private static void handleChat(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        // Read request body
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String sessionId = extractJsonValue(body, "sessionId");
        String message = extractJsonValue(body, "message");

        if (message == null || message.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"message is required\"}");
            return;
        }

        // Get or create session
        Session session = sessions.get(sessionId);
        if (session == null) {
            session = new Session();
            if (sessionId != null && !sessionId.isBlank()) {
                sessions.put(sessionId, session);
            }
            sessions.put(session.getSessionId(), session);
        }

        // Run agent
        log.separator();
        String response = agent.run(message, session);

        // Respond
        String json = "{\"response\":" + jsonEscape(response) + ",\"sessionId\":\"" + session.getSessionId() + "\"}";
        sendJson(exchange, 200, json);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        // CORS for local dev
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Simple JSON value extractor (no Jackson needed for this tiny API). */
    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) {
            search = "\"" + key + "\": \"";
            start = json.indexOf(search);
        }
        if (start == -1) return null;
        start += search.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && (end == start || json.charAt(end - 1) != '\\')) break;
            end++;
        }
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
