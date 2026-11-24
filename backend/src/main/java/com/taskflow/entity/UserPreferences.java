package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user preferences (Phase 3.0; Phase 0 had a single global row). A row is created with the column
 * defaults when the user is first provisioned.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferences {

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "daily_available_minutes", nullable = false)
    private Integer dailyAvailableMinutes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserPreferences(UUID userId, String timezone, Integer dailyAvailableMinutes, Instant updatedAt) {
        this.userId = userId;
        this.timezone = timezone;
        this.dailyAvailableMinutes = dailyAvailableMinutes;
        this.updatedAt = updatedAt;
    }
}
