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

/**
 * Single-user preferences for Phase 0. There is exactly one row with {@link #SINGLETON_ID}.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferences {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "daily_available_minutes", nullable = false)
    private Integer dailyAvailableMinutes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserPreferences(String timezone, Integer dailyAvailableMinutes, Instant updatedAt) {
        this.id = SINGLETON_ID;
        this.timezone = timezone;
        this.dailyAvailableMinutes = dailyAvailableMinutes;
        this.updatedAt = updatedAt;
    }
}
