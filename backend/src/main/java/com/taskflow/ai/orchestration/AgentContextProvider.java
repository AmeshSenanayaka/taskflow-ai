package com.taskflow.ai.orchestration;

import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.service.PreferencesService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Renders the "current context" block appended to every Phase 2 agent's system message at call time:
 * today's date in the user's timezone, the weekday and the daily available minutes. The prompts stay
 * static resources; this block is computed per invocation from the same {@code Clock} and preferences
 * the dashboard uses, so "overdue" means the same thing for the agents and for the REST API.
 */
@Component
public class AgentContextProvider {

    private final PreferencesService preferencesService;
    private final Clock clock;

    public AgentContextProvider(PreferencesService preferencesService, Clock clock) {
        this.preferencesService = preferencesService;
        this.clock = clock;
    }

    public String render() {
        PreferencesResponse preferences = preferencesService.get();
        ZoneId zone = preferencesService.getZoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return """

                Current context (authoritative, use it to resolve relative dates):
                - Today: %s (%s)
                - Timezone: %s
                - Daily available minutes: %s
                """.formatted(
                today,
                today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                zone.getId(),
                preferences.dailyAvailableMinutes() == null ? "unknown" : preferences.dailyAvailableMinutes());
    }
}
