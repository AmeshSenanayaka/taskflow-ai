package com.taskflow.dto.request;

import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Full update of an existing task (PUT semantics: every field is replaced)")
public record UpdateTaskRequest(

        @Schema(description = "Short title of the task", example = "Write the Phase 0 article", maxLength = 150)
        @NotBlank(message = "Title must not be blank")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Schema(description = "Optional longer description", maxLength = 2000)
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Schema(description = "Priority", example = "MEDIUM")
        @NotNull(message = "Priority is required")
        TaskPriority priority,

        @Schema(description = "Status. Moving to COMPLETED sets completedAt; leaving COMPLETED clears it", example = "IN_PROGRESS")
        @NotNull(message = "Status is required")
        TaskStatus status,

        @Schema(description = "Optional due date (ISO-8601, yyyy-MM-dd). Send null to clear", example = "2026-11-15")
        LocalDate dueDate
) {
}
