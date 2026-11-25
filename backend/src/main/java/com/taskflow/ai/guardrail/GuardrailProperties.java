package com.taskflow.ai.guardrail;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Phase 3.3 guardrail settings, bound from {@code taskflow.ai.guardrails.*}.
 *
 * @param enabled              turns every conversation guardrail on or off (the 3.2 rules are not affected)
 * @param maxWritesPerRequest  task changes one assistant message may make before tools refuse further writes
 * @param outputRetries        rewrites of an answer that failed an output check before the safe fallback is used
 */
@Validated
@ConfigurationProperties(prefix = "taskflow.ai.guardrails")
public record GuardrailProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") @Min(1) @Max(100) int maxWritesPerRequest,
        @DefaultValue("1") @Min(0) @Max(3) int outputRetries
) {
}
