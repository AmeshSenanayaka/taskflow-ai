package com.taskflow.rules.task;

import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * A due date may not be set or moved to a day before the owner's today. A task that already has a past due date
 * (an overdue task) stays valid: only a <em>new</em> past date is refused, so overdue tasks can still be edited.
 */
@Component
public class DueDateNotInPastRule implements DomainRule<TaskChange> {

    public static final String NAME = "TASK_DUE_DATE_NOT_IN_PAST";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(TaskChange change) {
        if (change.after().dueDate() == null || change.today() == null) {
            return RuleResult.ok();
        }
        boolean dateIsNew = change.isCreate() || !Objects.equals(change.before().dueDate(), change.after().dueDate());
        if (dateIsNew && change.after().dueDate().isBefore(change.today())) {
            return RuleResult.violation(NAME, "dueDate", "The due date " + change.after().dueDate()
                    + " is in the past (today is " + change.today() + "). Choose today or a later date.");
        }
        return RuleResult.ok();
    }
}
