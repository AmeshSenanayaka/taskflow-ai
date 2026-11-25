package com.taskflow.rules.plan;

import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** The planned minutes never exceed the user's daily available minutes from their preferences. */
@Component
public class PlanWithinAvailableMinutesRule implements DomainRule<PlanCheck> {

    public static final String NAME = "PLAN_WITHIN_AVAILABLE_MINUTES";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(PlanCheck plan) {
        int total = plan.blocks().stream().map(PlanCheck.Block::minutes).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        if (total <= plan.availableMinutes()) {
            return RuleResult.ok();
        }
        return RuleResult.violation(NAME, "blocks", "The plan has " + total + " minutes but the user has only "
                + plan.availableMinutes() + " available. Shorten or drop blocks and list the dropped tasks as deferred.");
    }
}
