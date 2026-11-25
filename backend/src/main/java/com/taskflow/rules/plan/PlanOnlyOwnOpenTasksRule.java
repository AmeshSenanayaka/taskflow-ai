package com.taskflow.rules.plan;

import com.taskflow.dto.response.TaskResponse;
import com.taskflow.enums.TaskStatus;
import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleResult;
import com.taskflow.rules.RuleViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every block is for one of the user's own open tasks, and each task appears once. An id the user does not own
 * looks exactly like an unknown id, so a plan cannot be used to probe other users' tasks.
 */
@Component
public class PlanOnlyOwnOpenTasksRule implements DomainRule<PlanCheck> {

    public static final String NAME = "PLAN_ONLY_OWN_OPEN_TASKS";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(PlanCheck plan) {
        List<RuleViolation> violations = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (PlanCheck.Block block : plan.blocks()) {
            if (block.taskId() == null) {
                continue; // reported by PlanWellFormedRule
            }
            TaskResponse task = plan.ownTasks().get(block.taskId());
            if (task == null) {
                violations.add(new RuleViolation(NAME, "blocks", "Task " + block.taskId()
                        + " was not found among the user's tasks. Use only ids returned by findTasks."));
            } else if (task.status() == TaskStatus.COMPLETED) {
                violations.add(new RuleViolation(NAME, "blocks", "\"" + task.title()
                        + "\" is already completed and must not be planned."));
            }
            if (!seen.add(block.taskId())) {
                violations.add(new RuleViolation(NAME, "blocks", "Task " + block.taskId()
                        + " appears more than once. Plan each task in a single block."));
            }
        }
        return new RuleResult(violations);
    }
}
