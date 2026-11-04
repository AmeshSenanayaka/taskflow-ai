package com.taskflow.dto.request;

import com.taskflow.enums.TaskPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Payload for creating a new task")
public record CreateTaskRequest(

        @Schema(description = "Short title of the task", example = "Write the Phase 0 article", maxLength = 150)
        @NotBlank(message = "Title must not be blank")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Schema(description = "Optional longer description", example = "Cover architecture, API and UI.", maxLength = 2000)
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Schema(description = "Priority. Defaults to MEDIUM when omitted", example = "HIGH")
        TaskPriority priority,

        @Schema(description = "Optional due date (ISO-8601, yyyy-MM-dd)", example = "2026-11-15")
        LocalDate dueDate
) {
}
