package com.taskflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A specialised agent available behind the supervisor")
public record AgentDescriptor(
        @Schema(example = "PriorityAgent") String name,
        @Schema(description = "What the supervisor's planner reads to decide whether to call this agent") String description,
        @Schema(description = "False for agents that only read task data", example = "false") boolean canModifyTasks
) {
}
