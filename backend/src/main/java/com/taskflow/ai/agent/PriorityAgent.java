package com.taskflow.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Analyses the user's open tasks and recommends what to work on first.
 * <p>
 * The agent is read-only: it fetches tasks and statistics through {@code TaskQueryTools} and returns a
 * ranked recommendation with reasons. It never changes a task; if the user wants a priority applied,
 * the supervisor routes that follow-up to the {@link TaskAgent}.
 */
public interface PriorityAgent {

    String NAME = "PriorityAgent";
    String OUTPUT_KEY = "priorityAnalysis";

    @Agent(
            name = NAME,
            description = """
                    Analyses the user's open tasks and recommends which ones to work on first, ranking them by
                    urgency (due date, overdue), importance (priority) and status, with a short reason for each.
                    Use it for questions like "what should I focus on", "which tasks are most urgent" or
                    "is my prioritisation right". It only recommends; it does not change tasks.
                    """,
            outputKey = OUTPUT_KEY
    )
    @SystemMessage(fromResource = "/prompts/priority-agent-system.txt")
    @UserMessage("{{request}}")
    String analyse(@V("request") String request);
}
