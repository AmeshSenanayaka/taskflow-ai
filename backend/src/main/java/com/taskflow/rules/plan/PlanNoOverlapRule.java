package com.taskflow.rules.plan;

import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleResult;
import com.taskflow.rules.RuleViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Time blocks do not overlap: one thing at a time. */
@Component
public class PlanNoOverlapRule implements DomainRule<PlanCheck> {

    public static final String NAME = "PLAN_NO_OVERLAP";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(PlanCheck plan) {
        List<PlanCheck.Block> timed = plan.blocks().stream()
                .filter(block -> block.start() != null && block.minutes() != null)
                .sorted(Comparator.comparing(PlanCheck.Block::start))
                .toList();
        List<RuleViolation> violations = new ArrayList<>();
        for (int i = 1; i < timed.size(); i++) {
            PlanCheck.Block previous = timed.get(i - 1);
            PlanCheck.Block current = timed.get(i);
            int previousEnd = previous.start().toSecondOfDay() / 60 + previous.minutes();
            int currentStart = current.start().toSecondOfDay() / 60;
            if (currentStart < previousEnd) {
                violations.add(new RuleViolation(NAME, "blocks", "Block " + current.raw() + " starts before block "
                        + previous.raw() + " ends."));
            }
        }
        return new RuleResult(violations);
    }
}
