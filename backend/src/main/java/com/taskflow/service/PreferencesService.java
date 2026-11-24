package com.taskflow.service;

import com.taskflow.dto.request.UpdatePreferencesRequest;
import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.entity.UserPreferences;
import com.taskflow.mapper.PreferencesMapper;
import com.taskflow.repository.PreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Per-user preferences (Phase 3.0). Every method takes the user id supplied by the authenticated caller. */
@Service
@Transactional(readOnly = true)
public class PreferencesService {

    private final PreferencesRepository preferencesRepository;
    private final PreferencesMapper preferencesMapper;
    private final Clock clock;

    public PreferencesService(PreferencesRepository preferencesRepository,
                              PreferencesMapper preferencesMapper,
                              Clock clock) {
        this.preferencesRepository = preferencesRepository;
        this.preferencesMapper = preferencesMapper;
        this.clock = clock;
    }

    public PreferencesResponse get(UUID userId) {
        return preferencesMapper.toResponse(load(userId));
    }

    /**
     * The timezone used for every "today"-based calculation (dashboard, overdue, due today).
     */
    public ZoneId getZoneId(UUID userId) {
        return ZoneId.of(load(userId).getTimezone());
    }

    /**
     * The user's current calendar date: the single definition of "today" shared by the dashboard,
     * due-today/overdue queries and the AI tools, so every caller agrees on which day it is.
     * <p>
     * Contract: a task's {@code dueDate} is a calendar date ({@link LocalDate}), not an instant. Changing the
     * timezone never moves a stored due date; it only changes which calendar day counts as "today", and
     * therefore which tasks are currently overdue or due today. The server's own timezone is irrelevant.
     */
    public LocalDate today(UUID userId) {
        return LocalDate.now(clock.withZone(getZoneId(userId)));
    }

    @Transactional
    public PreferencesResponse update(UUID userId, UpdatePreferencesRequest request) {
        UserPreferences preferences = load(userId);
        preferences.setTimezone(request.timezone());
        preferences.setDailyAvailableMinutes(request.dailyAvailableMinutes());
        preferences.setUpdatedAt(Instant.now(clock));
        return preferencesMapper.toResponse(preferencesRepository.saveAndFlush(preferences));
    }

    private UserPreferences load(UUID userId) {
        // The row is created together with the user (UserAccountService), so it always exists for a real caller.
        return preferencesRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Preferences are missing for user " + userId));
    }
}
