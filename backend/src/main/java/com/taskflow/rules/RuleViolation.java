package com.taskflow.rules;

/**
 * One broken business rule.
 *
 * @param rule    stable rule code, e.g. {@code TASK_DUE_DATE_NOT_IN_PAST}; clients and evals can rely on it
 * @param field   the input field the rule is about, or null when it concerns the whole change
 * @param message a sentence a user (or a model) can act on
 */
public record RuleViolation(String rule, String field, String message) {
}
