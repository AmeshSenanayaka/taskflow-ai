package com.taskflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One tool execution performed by an agent")
public record ToolCallInfo(
        @Schema(example = "findTasks") String name,
        @Schema(example = "14") long durationMs,
        boolean success
) {
}
