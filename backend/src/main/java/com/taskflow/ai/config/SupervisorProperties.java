package com.taskflow.ai.config;

import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables of the Phase 2 multi-agent system, bound from {@code taskflow.ai.supervisor.*}.
 *
 * @param maxAgentInvocations upper bound on sub-agent calls per user request; stops a confused planner
 *                            from looping (each invocation costs at least two LLM calls)
 * @param maxToolRoundTrips   upper bound on tool calls inside one sub-agent invocation
 * @param memoryMaxMessages   sliding-window size of the supervisor's own chat memory per conversation
 * @param responseStrategy    how the final answer is chosen: LAST (last sub-agent's answer, the default),
 *                            SUMMARY (the planner's recap) or SCORED (an extra LLM call scores both)
 * @param contextStrategy     how the planner remembers earlier turns of a conversation
 */
@Validated
@ConfigurationProperties(prefix = "taskflow.ai.supervisor")
public record SupervisorProperties(
        @DefaultValue("5") @Min(1) @Max(20) int maxAgentInvocations,
        @DefaultValue("8") @Min(1) @Max(30) int maxToolRoundTrips,
        @DefaultValue("20") @Min(2) @Max(200) int memoryMaxMessages,
        @DefaultValue("LAST") @NotNull SupervisorResponseStrategy responseStrategy,
        @DefaultValue("CHAT_MEMORY") @NotNull SupervisorContextStrategy contextStrategy
) {
}
