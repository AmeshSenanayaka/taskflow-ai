package com.taskflow.rules.task;

import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleResult;
import org.springframework.stereotype.Component;

/**
 * A user cannot have two open (not completed) tasks with the same title, ignoring case. Completed tasks do not
 * count, so a recurring chore can be created again once the previous one is done.
 * <p>
 * Checked when a task is created, renamed or reopened; an edit that touches neither title nor status does not
 * fail because of duplicates that existed before the rule.
 */
@Component
public class UniqueOpenTitleRule implements DomainRule<TaskChange> {

    public static final String NAME = "TASK_UNIQUE_OPEN_TITLE";

    private final TaskRepository tasks;

    public UniqueOpenTitleRule(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(TaskChange change) {
        TaskChange.State after = change.after();
        if (after.status() == TaskStatus.COMPLETED || after.title() == null) {
            return RuleResult.ok();
        }
        boolean relevant = change.isCreate()
                || !after.title().equalsIgnoreCase(change.before().title())
                || change.before().status() == TaskStatus.COMPLETED;
        if (!relevant) {
            return RuleResult.ok();
        }
        String title = after.title().trim();
        boolean duplicate = change.isCreate()
                ? tasks.existsByOwnerIdAndStatusNotAndTitleIgnoreCase(change.ownerId(), TaskStatus.COMPLETED, title)
                : tasks.existsByOwnerIdAndStatusNotAndTitleIgnoreCaseAndIdNot(
                        change.ownerId(), TaskStatus.COMPLETED, title, change.taskId());
        return duplicate
                ? RuleResult.violation(NAME, "title", "You already have an open task called \"" + title + "\".")
                : RuleResult.ok();
    }
}
