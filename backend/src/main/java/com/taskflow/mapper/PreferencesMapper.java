package com.taskflow.mapper;

import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.entity.UserPreferences;
import org.springframework.stereotype.Component;

@Component
public class PreferencesMapper {

    public PreferencesResponse toResponse(UserPreferences preferences) {
        return new PreferencesResponse(
                preferences.getTimezone(),
                preferences.getDailyAvailableMinutes(),
                preferences.getUpdatedAt()
        );
    }
}
