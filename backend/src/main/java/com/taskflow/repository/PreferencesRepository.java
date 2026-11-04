package com.taskflow.repository;

import com.taskflow.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferencesRepository extends JpaRepository<UserPreferences, Short> {
}
