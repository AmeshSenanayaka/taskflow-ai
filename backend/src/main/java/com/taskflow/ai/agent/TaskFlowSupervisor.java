package com.taskflow.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;

/**
 * The Phase 2 entry point: a LangChain4j supervisor agent that reads the user's request, decides which
 * specialised agent(s) to call ({@link TaskAgent}, {@link PriorityAgent}, {@link PlanningAgent},
 * {@link SummaryAgent}), runs them in sequence and returns the final answer.
 * <p>
 * LangChain4j implements this interface at runtime ({@code AgenticServices.supervisorBuilder}). The
 * planner is itself an LLM call: it only sees the sub-agents' names, descriptions and arguments and
 * decides the next invocation step by step until it answers "done". {@code @MemoryId} gives the
 * supervisor its own chat memory per conversation, and the same id is forwarded to sub-agents that
 * declare one (the {@link TaskAgent}), so multi-turn follow-ups work across the whole system.
 */
public interface TaskFlowSupervisor {

    String NAME = "TaskFlowSupervisor";

    @Agent(
            name = NAME,
            description = "Routes task-management requests to the specialised TaskFlow agents and composes the answer"
    )
    String chat(@MemoryId String conversationId,
                @V("request") String request);
}
