package com.taskflow.ai.memory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Phase 3.4 persistent conversation memory.
 *
 * @param maxMessages      TaskAgent's sliding window (messages the model sees), as in Phase 1
 * @param retention        conversations without activity for this long are deleted with their memory and transcript
 * @param maxConversations conversations kept per user; the oldest are deleted when a new one is started
 * @param maxStoredBytes   upper bound on one stored memory window, in characters of JSON (close to bytes for
 *                         mostly-ASCII text); the oldest messages are dropped above it
 * @param cleanupInterval  how often the retention job runs
 */
@Validated
@ConfigurationProperties(prefix = "taskflow.ai.memory")
public record MemoryProperties(
        @DefaultValue("30") @Min(2) @Max(500) int maxMessages,
        @DefaultValue("30d") Duration retention,
        @DefaultValue("100") @Min(1) @Max(10000) int maxConversations,
        @DefaultValue("262144") @Min(1024) int maxStoredBytes,
        @DefaultValue("PT1H") Duration cleanupInterval
) {
}
