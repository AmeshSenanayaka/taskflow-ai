package com.taskflow.rules.task;

import com.taskflow.enums.TaskStatus;
import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * A completed task keeps its priority and due date: they describe work that is finished. To change them the task
 * must be reopened first (which may happen in the same request: a change that also leaves COMPLETED is allowed).
 */
@Component
public class CompletedTaskIsFrozenRule implements DomainRule<TaskChange> {

    public static final String NAME = "TASK_COMPLETED_IS_FROZEN";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(TaskChange change) {
        if (change.isCreate()
                || change.before().status() != TaskStatus.COMPLETED
                || change.after().status() != TaskStatus.COMPLETED) {
            return RuleResult.ok();
        }
        boolean priorityChanged = change.before().priority() != change.after().priority();
        boolean dueDateChanged = !Objects.equals(change.before().dueDate(), change.after().dueDate());
        if (!priorityChanged && !dueDateChanged) {
            return RuleResult.ok();
        }
        String field = priorityChanged ? "priority" : "dueDate";
        return RuleResult.violation(NAME, field,
                "A completed task cannot change its priority or due date. Reopen the task first.");
    }
}
