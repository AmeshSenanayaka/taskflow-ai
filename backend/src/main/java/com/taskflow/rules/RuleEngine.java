package com.taskflow.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a set of rules against one context and reports every violation at once, not just the first, so a user or
 * a model can fix all problems in one go.
 */
public final class RuleEngine {

    private RuleEngine() {
    }

    public static <T> List<RuleViolation> evaluate(List<? extends DomainRule<T>> rules, T context) {
        List<RuleViolation> violations = new ArrayList<>();
        for (DomainRule<T> rule : rules) {
            violations.addAll(rule.evaluate(context).violations());
        }
        return violations;
    }

    /** @throws RuleViolationException when any rule is broken */
    public static <T> void enforce(List<? extends DomainRule<T>> rules, T context) {
        List<RuleViolation> violations = evaluate(rules, context);
        if (!violations.isEmpty()) {
            throw new RuleViolationException(violations);
        }
    }
}
