package com.taskflow.dto.response;

import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "A task as returned by the API")
public record TaskResponse(
        @Schema(example = "3f0e2a5c-9c1a-4f7b-9a3e-2d6b3e1a9c11") UUID id,
        @Schema(example = "Write the Phase 0 article") String title,
        @Schema(example = "Cover architecture, API and UI.") String description,
        @Schema(example = "HIGH") TaskPriority priority,
        @Schema(example = "TODO") TaskStatus status,
        @Schema(example = "2026-11-15") LocalDate dueDate,
        @Schema(example = "2026-11-02T10:15:30Z") Instant createdAt,
        @Schema(example = "2026-11-02T10:15:30Z") Instant updatedAt,
        @Schema(description = "Set only while the task is COMPLETED", example = "2026-11-03T08:00:00Z") Instant completedAt
) {
}
