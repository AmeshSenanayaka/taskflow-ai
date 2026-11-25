package com.taskflow.rules.plan;

import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleResult;
import com.taskflow.rules.RuleViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** A plan has a date that is not in the past and at least one block, each with a task id, a start and 5-1440 minutes inside the day. */
@Component
public class PlanWellFormedRule implements DomainRule<PlanCheck> {

    public static final String NAME = "PLAN_WELL_FORMED";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(PlanCheck plan) {
        List<RuleViolation> violations = new ArrayList<>();
        if (plan.date() == null) {
            violations.add(new RuleViolation(NAME, "date", "The plan date must be a valid YYYY-MM-DD date."));
        } else if (plan.today() != null && plan.date().isBefore(plan.today())) {
            violations.add(new RuleViolation(NAME, "date", "The plan date " + plan.date() + " is in the past."));
        }
        if (plan.blocks().isEmpty()) {
            violations.add(new RuleViolation(NAME, "blocks", "The plan has no blocks. Do not submit a plan when "
                    + "there is nothing to schedule; tell the user instead."));
        }
        for (PlanCheck.Block block : plan.blocks()) {
            if (block.taskId() == null || block.start() == null || block.minutes() == null) {
                violations.add(new RuleViolation(NAME, "blocks", "Block " + block.raw()
                        + " needs a valid taskId, a start time as HH:mm and minutes."));
            } else if (block.minutes() < 5 || block.minutes() > 1440) {
                violations.add(new RuleViolation(NAME, "blocks", "Block " + block.raw()
                        + " must last between 5 and 1440 minutes."));
            } else if (block.start().toSecondOfDay() / 60 + block.minutes() > 24 * 60) {
                violations.add(new RuleViolation(NAME, "blocks", "Block " + block.raw() + " runs past midnight."));
            }
        }
        return new RuleResult(violations);
    }
}
