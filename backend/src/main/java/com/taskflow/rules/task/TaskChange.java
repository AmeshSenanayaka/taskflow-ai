package com.taskflow.rules.task;

import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A proposed change to one task, as the task rules see it.
 *
 * @param ownerId the user who owns the task
 * @param taskId  the task's id, or null when the task is being created
 * @param before  the stored state, or null when the task is being created
 * @param after   the state that would be stored
 * @param today   the owner's current date in their timezone
 */
public record TaskChange(UUID ownerId, UUID taskId, State before, State after, LocalDate today) {

    /** The fields the rules look at. */
    public record State(String title, TaskPriority priority, TaskStatus status, LocalDate dueDate) {

        public static State of(Task task) {
            return new State(task.getTitle(), task.getPriority(), task.getStatus(), task.getDueDate());
        }
    }

    public boolean isCreate() {
        return before == null;
    }
}
