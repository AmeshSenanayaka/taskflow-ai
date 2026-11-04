package com.taskflow.dto.request;

import com.taskflow.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for changing only the status of a task")
public record UpdateTaskStatusRequest(

        @Schema(description = "New status", example = "COMPLETED")
        @NotNull(message = "Status is required")
        TaskStatus status
) {
}
