package com.taskflow.ai.dto;

import com.taskflow.enums.TaskPriority;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A day plan that passed every plan rule (Phase 3.2). PlanningAgent proposes it through the {@code submitPlan}
 * tool; only an accepted plan is returned to the client, so the UI and the evaluations can rely on it.
 */
@Schema(description = "A validated plan for one day")
public record DailyPlan(
        @Schema(example = "2026-11-09") LocalDate date,
        @Schema(description = "The user's daily available minutes", example = "480") int availableMinutes,
        @Schema(description = "Planned minutes, never more than availableMinutes", example = "180") int totalMinutes,
        List<Entry> entries
) {

    @Schema(description = "One time block, for one of the user's open tasks")
    public record Entry(UUID taskId, String title, TaskPriority priority, LocalDate dueDate,
                        @Schema(example = "09:00") String start, @Schema(example = "10:30") String end,
                        @Schema(example = "90") int minutes) {
    }
}
