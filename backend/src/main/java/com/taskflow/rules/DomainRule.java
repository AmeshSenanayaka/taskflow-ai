package com.taskflow.rules;

/**
 * A deterministic business rule (Phase 3.2). A rule only reads the context it is given and returns violations;
 * it never changes data and never depends on who called (UI, REST API or an AI tool), so the same rules apply
 * to every path into the system.
 *
 * @param <T> what the rule checks, e.g. a proposed task change or a proposed day plan
 */
public interface DomainRule<T> {

    /** Stable rule code, used in violations. */
    String name();

    RuleResult evaluate(T context);
}
