package com.taskflow.repository;

import com.taskflow.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** One preferences row per user, keyed by the user id. */
public interface PreferencesRepository extends JpaRepository<UserPreferences, UUID> {

    /** Gives a new user the default preferences (column defaults); a no-op if the user already has a row. */
    @Modifying
    @Query(value = """
            INSERT INTO user_preferences (user_id)
            VALUES (:userId)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertDefaultsIfAbsent(@Param("userId") UUID userId);
}
