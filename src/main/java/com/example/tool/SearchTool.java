package com.example.tool;

import java.util.Map;

/**
 * Mock search tool -- returns canned results based on keyword matching.
 *
 * In a real agent, this would call an actual search API (Google, Bing, etc.).
 * Here we simulate it to demonstrate the tool-calling flow.
 */
public class SearchTool implements Tool {

    // A minimal knowledge base for mock search
    private static final Map<String, String> KNOWLEDGE = Map.ofEntries(
        Map.entry("java", """
            Java is a high-level, class-based, object-oriented programming language.
            Latest LTS version: Java 21 (September 2023). Key features: virtual threads,
            pattern matching, sealed classes, records, and text blocks.
            """),
        Map.entry("python", """
            Python is a high-level, interpreted programming language known for its
            readability. Latest version: Python 3.13. Popular for AI/ML, web development,
            automation, and data science.
            """),
        Map.entry("ai", """
            Artificial Intelligence (AI) is the simulation of human intelligence by machines.
            Key subfields: machine learning, natural language processing, computer vision,
            and robotics. Large Language Models (LLMs) like GPT-4 and Claude power modern AI agents.
            """),
        Map.entry("agent", """
            An AI Agent is an autonomous system that perceives its environment, makes decisions,
            and takes actions to achieve goals. Key components: LLM for reasoning, tools for
            actions, memory for context, and a decision loop (observe -> plan -> act -> observe).
            """),
        Map.entry("weather", """
            Current weather (mock data): Sunny, 22°C (72°F), humidity 45%, wind 12 km/h.
            Forecast: Clear skies for the next 3 days with temperatures ranging 18-25°C.
            """),
        Map.entry("time", "Current server time: " + java.time.LocalDateTime.now().toString()),
        Map.entry("maven", """
            Apache Maven is a build automation tool for Java projects. Uses pom.xml for
            configuration. Key concepts: dependencies, plugins, lifecycles (clean, compile,
            test, package, install, deploy).
            """),
        Map.entry("git", """
            Git is a distributed version control system. Key commands: clone, add, commit,
            push, pull, branch, merge, rebase. GitHub/GitLab are popular hosting platforms.
            """)
    );

    @Override
    public String name() { return "search"; }

    @Override
    public String description() {
        return "Search for information on a given topic. Returns relevant knowledge if available. Use this when you need factual information you don't already know.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of(
                    "type", "string",
                    "description", "The search query or topic to look up, e.g. 'Java', 'weather', 'AI'"
                )
            ),
            "required", java.util.List.of("query")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args, Map<String, Object> sessionState) {
        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.error("Missing 'query' argument");
        }

        String queryLower = query.toLowerCase();

        // Try exact match first
        for (var entry : KNOWLEDGE.entrySet()) {
            if (queryLower.contains(entry.getKey())) {
                return ToolResult.success("Search result for \"" + query + "\":\n" + entry.getValue());
            }
        }

        // No match
        return ToolResult.success(
            "Search result for \"" + query + "\": No specific information found. " +
            "Available topics: " + String.join(", ", KNOWLEDGE.keySet()) + "."
        );
    }
}
