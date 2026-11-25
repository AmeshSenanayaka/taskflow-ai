package com.taskflow.rules;

import java.util.List;

/** The outcome of evaluating one rule: no violations means the rule holds. */
public record RuleResult(List<RuleViolation> violations) {

    private static final RuleResult OK = new RuleResult(List.of());

    public static RuleResult ok() {
        return OK;
    }

    public static RuleResult violation(String rule, String field, String message) {
        return new RuleResult(List.of(new RuleViolation(rule, field, message)));
    }

    public boolean passed() {
        return violations.isEmpty();
    }
}
