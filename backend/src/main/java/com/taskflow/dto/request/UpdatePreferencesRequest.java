package com.taskflow.dto.request;

import com.taskflow.validation.ValidTimezone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for updating the single-user preferences")
public record UpdatePreferencesRequest(

        @Schema(description = "IANA timezone identifier", example = "Asia/Colombo")
        @NotBlank(message = "Timezone is required")
        @ValidTimezone
        String timezone,

        @Schema(description = "Minutes available for tasks per day (0-1440)", example = "480", minimum = "0", maximum = "1440")
        @NotNull(message = "Daily available minutes is required")
        @Min(value = 0, message = "Daily available minutes must be at least 0")
        @Max(value = 1440, message = "Daily available minutes must be at most 1440")
        Integer dailyAvailableMinutes
) {
}
