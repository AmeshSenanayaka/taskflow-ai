package com.taskflow.service;

import com.taskflow.dto.request.UpdatePreferencesRequest;
import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.entity.UserPreferences;
import com.taskflow.mapper.PreferencesMapper;
import com.taskflow.repository.PreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class PreferencesService {

    private final PreferencesRepository preferencesRepository;
    private final PreferencesMapper preferencesMapper;

    public PreferencesService(PreferencesRepository preferencesRepository, PreferencesMapper preferencesMapper) {
        this.preferencesRepository = preferencesRepository;
        this.preferencesMapper = preferencesMapper;
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

    @Transactional
    public PreferencesResponse update(UpdatePreferencesRequest request) {
        UserPreferences preferences = load();
        preferences.setTimezone(request.timezone());
        preferences.setDailyAvailableMinutes(request.dailyAvailableMinutes());
        // Flush so @PreUpdate runs and the response carries the fresh updatedAt.
        return preferencesMapper.toResponse(preferencesRepository.saveAndFlush(preferences));
    }

    private UserPreferences load() {
        // The row is seeded by Flyway migration V2, so it always exists.
        return preferencesRepository.findById(UserPreferences.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "user_preferences row is missing; run the Flyway migrations"));
    }
}
