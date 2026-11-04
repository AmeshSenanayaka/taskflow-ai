package com.taskflow.specification;

import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Filter criteria for listing tasks. Every field is optional; {@code null} means "no filter".
 */
public record TaskFilter(
        List<TaskStatus> statuses,
        TaskPriority priority,
        LocalDate dueDate,
        LocalDate dueDateFrom,
        LocalDate dueDateTo,
        String search
) {

    public static TaskFilter none() {
        return new TaskFilter(null, null, null, null, null, null);
    }
}
