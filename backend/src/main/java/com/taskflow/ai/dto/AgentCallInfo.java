package com.taskflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One sub-agent invocation made by the supervisor while answering a request")
public record AgentCallInfo(
        @Schema(example = "PlanningAgent") String name,
        @Schema(example = "1830") long durationMs,
        boolean success
) {
}
