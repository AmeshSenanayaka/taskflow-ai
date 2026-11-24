package com.taskflow.ai.orchestration;

import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.PreferencesService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;

/**
 * Renders the "current context" block appended to every Phase 2 agent's system message at call time:
 * today's date in the user's timezone, the weekday and the daily available minutes. The prompts stay
 * static resources; this block is computed per invocation from the same {@code Clock} and preferences
 * the dashboard uses, so "overdue" means the same thing for the agents and for the REST API.
 * Since Phase 3.0 the preferences are the authenticated caller's.
 */
@Component
public class AgentContextProvider {

    private final PreferencesService preferencesService;
    private final Clock clock;
    private final CurrentUserProvider currentUser;

    public AgentContextProvider(PreferencesService preferencesService, Clock clock, CurrentUserProvider currentUser) {
        this.preferencesService = preferencesService;
        this.clock = clock;
        this.currentUser = currentUser;
    }

    public String render() {
        UUID userId = currentUser.userId();
        PreferencesResponse preferences = preferencesService.get(userId);
        ZoneId zone = preferencesService.getZoneId(userId);
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
