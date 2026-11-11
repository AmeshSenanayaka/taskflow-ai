package com.taskflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Answer of the TaskFlowSupervisor, with the agents and tools it used")
public record SupervisorChatResponse(
        @Schema(example = "6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f") String conversationId,
        @Schema(description = "The reply shown to the user") String message,
        @Schema(description = "True when a tool changed task data, so clients should refresh lists and statistics")
        boolean dataChanged,
        Execution execution
) {

    @Schema(description = "How the request was processed")
    public record Execution(
            @Schema(description = "Correlation id, also present in the backend logs") String requestId,
            @Schema(example = "3210") long durationMs,
            @Schema(description = "Sub-agents invoked by the supervisor, in order") List<AgentCallInfo> agents,
            @Schema(description = "Tool calls made by those agents, in order") List<ToolCallInfo> toolCalls,
            @Schema(description = "Number of LLM calls (planner + agents + tool rounds)", example = "4") int llmCalls,
            TokenUsageInfo tokenUsage
    ) {
    }

    @Schema(description = "Token usage summed over every LLM call of the request (null when the model does not report it)")
    public record TokenUsageInfo(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
    }
}
