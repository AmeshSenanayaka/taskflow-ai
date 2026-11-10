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

    public PreferencesResponse get() {
        return preferencesMapper.toResponse(load());
    }

    /**
     * The timezone used for every "today"-based calculation (dashboard, overdue, due today).
     */
    public ZoneId getZoneId() {
        return ZoneId.of(load().getTimezone());
    }

    /**
     * The user's current calendar date: the single definition of "today" shared by the dashboard,
     * due-today/overdue queries and the AI tools, so every caller agrees on which day it is.
     * <p>
     * Contract: a task's {@code dueDate} is a calendar date ({@link LocalDate}), not an instant. Changing the
     * timezone never moves a stored due date; it only changes which calendar day counts as "today", and
     * therefore which tasks are currently overdue or due today. The server's own timezone is irrelevant.
     */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(getZoneId()));
    }

    @Transactional
    public PreferencesResponse update(UpdatePreferencesRequest request) {
        UserPreferences preferences = load();
        preferences.setTimezone(request.timezone());
        preferences.setDailyAvailableMinutes(request.dailyAvailableMinutes());
        preferences.setUpdatedAt(Instant.now(clock));
        return preferencesMapper.toResponse(preferencesRepository.saveAndFlush(preferences));
    }

    private UserPreferences load() {
        // The row is seeded by Flyway migration V2, so it always exists.
        return preferencesRepository.findById(UserPreferences.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "user_preferences row is missing; run the Flyway migrations"));
    }
}
