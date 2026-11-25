package com.taskflow.ai.guardrail;

/** One guardrail finding: a stable code and a sentence the model (or a developer) can act on. */
public record GuardrailViolation(String code, String message) {
}
