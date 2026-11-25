package com.taskflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "One line of a conversation's transcript, as it was shown (Phase 3.4)")
public record ConversationMessage(
        @Schema(example = "assistant", allowableValues = {"user", "assistant"}) String role,
        String content,
        Instant createdAt,
        @Schema(description = "The validated plan shown with the answer, if any") DailyPlan plan,
        @Schema(description = "Guardrail codes that acted on the turn") List<String> guardrails
) {
}
