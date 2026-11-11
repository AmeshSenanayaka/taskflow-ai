package com.taskflow.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Builds a realistic execution plan for the day (or the period the user asks for) from the user's real
 * tasks, their priorities and due dates, and the daily available minutes from the preferences.
 * <p>
 * Read-only: it fetches tasks and the planning context through {@code TaskQueryTools} and never changes
 * task data. Time estimates are explicit assumptions, not facts.
 */
public interface PlanningAgent {

    String NAME = "PlanningAgent";
    String OUTPUT_KEY = "dailyPlan";

    @Agent(
            name = NAME,
            description = """
                    Creates a realistic, time-boxed plan for the user's day or week from their real tasks,
                    using task priorities, due dates and the daily available working time from the user's
                    preferences. Use it for requests like "plan my day", "what should my schedule look like"
                    or "how do I fit these tasks into today". It only plans; it does not change tasks.
                    """,
            outputKey = OUTPUT_KEY
    )
    @SystemMessage(fromResource = "/prompts/planning-agent-system.txt")
    @UserMessage("""
            Create a practical task execution plan.

            Consider:
            - Task priorities
            - Due dates
            - Available time
            - Reasonable scheduling assumptions

            Do not invent task details or deadlines.
            Explain assumptions when information is missing.

            Request: {{request}}
            """)
    String plan(@V("request") String request);
}
