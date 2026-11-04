package com.taskflow.service;

import com.taskflow.dto.response.DashboardStatsResponse;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final TaskRepository taskRepository;
    private final PreferencesService preferencesService;
    private final Clock clock;

    public DashboardService(TaskRepository taskRepository, PreferencesService preferencesService, Clock clock) {
        this.taskRepository = taskRepository;
        this.preferencesService = preferencesService;
        this.clock = clock;
    }

    public DashboardStatsResponse getStats() {
        // "Today" depends on the user's timezone, not on the server's.
        LocalDate today = LocalDate.now(clock.withZone(preferencesService.getZoneId()));

        return new DashboardStatsResponse(
                taskRepository.count(),
                taskRepository.countByStatus(TaskStatus.TODO),
                taskRepository.countByStatus(TaskStatus.IN_PROGRESS),
                taskRepository.countByStatus(TaskStatus.COMPLETED),
                taskRepository.countOverdue(today),
                taskRepository.countDueToday(today)
        );
    }
}
