package com.taskflow.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Summarises the state of the user's tasks: progress, what is overdue, what is due soon and what was
 * completed recently. Read-only; it fetches statistics and tasks through {@code TaskQueryTools}.
 */
public interface SummaryAgent {

    String NAME = "SummaryAgent";
    String OUTPUT_KEY = "summary";

    @Agent(
            name = NAME,
            description = """
                    Summarises the current state of the user's tasks: overall progress, counts by status,
                    overdue and due-soon tasks, and recently completed work. Use it for requests like
                    "summarise my progress", "how am I doing this week" or "give me a status overview".
                    It only reports; it does not change tasks.
                    """,
            outputKey = OUTPUT_KEY
    )
    @SystemMessage(fromResource = "/prompts/summary-agent-system.txt")
    @UserMessage("{{request}}")
    String summarise(@V("request") String request);
}
