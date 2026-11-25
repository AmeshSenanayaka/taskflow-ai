package com.taskflow.rules;

import java.util.List;
import java.util.stream.Collectors;

/** A change was refused because it breaks one or more business rules. Nothing was written. */
public class RuleViolationException extends RuntimeException {

    private final List<RuleViolation> violations;

    public RuleViolationException(List<RuleViolation> violations) {
        super(violations.stream().map(RuleViolation::message).collect(Collectors.joining(" ")));
        this.violations = List.copyOf(violations);
    }

    public List<RuleViolation> violations() {
        return violations;
    }
}
