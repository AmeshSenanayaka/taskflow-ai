package com.taskflow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Single-user preferences")
public record PreferencesResponse(
        @Schema(description = "IANA timezone identifier", example = "Asia/Colombo") String timezone,
        @Schema(description = "Minutes available for tasks per day", example = "480") Integer dailyAvailableMinutes,
        @Schema(example = "2026-11-02T10:15:30Z") Instant updatedAt
) {
}
