package com.taskflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "One of the caller's AI conversations (Phase 3.4)")
public record ConversationSummary(
        @Schema(example = "6f1c2b7e-1d1a-4a43-9a4c-0e8f1c7b2d11") String conversationId,
        @Schema(description = "The start of the first message, or null before the first message",
                example = "Plan my day") String title,
        @Schema(description = "agent (Phase 1 TaskAgent) or supervisor (multi-agent)", example = "supervisor") String mode,
        Instant createdAt,
        @Schema(description = "Last activity; conversations are deleted after the retention period") Instant updatedAt
) {
}
