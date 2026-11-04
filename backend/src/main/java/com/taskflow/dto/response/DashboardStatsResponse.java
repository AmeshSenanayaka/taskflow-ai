package com.taskflow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregated task counts for the dashboard")
public record DashboardStatsResponse(
        @Schema(description = "All tasks", example = "20") long total,
        @Schema(description = "Tasks in TODO", example = "8") long pending,
        @Schema(description = "Tasks in IN_PROGRESS", example = "5") long inProgress,
        @Schema(description = "Tasks in COMPLETED", example = "7") long completed,
        @Schema(description = "Incomplete tasks whose due date is before today (user timezone)", example = "2") long overdue,
        @Schema(description = "Incomplete tasks due today (user timezone)", example = "3") long dueToday
) {
}
