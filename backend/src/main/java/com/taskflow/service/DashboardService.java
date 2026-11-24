package com.taskflow.service;

import com.taskflow.dto.response.DashboardStatsResponse;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final TaskRepository taskRepository;
    private final PreferencesService preferencesService;

    public DashboardService(TaskRepository taskRepository, PreferencesService preferencesService) {
        this.taskRepository = taskRepository;
        this.preferencesService = preferencesService;
    }

    /** Statistics over one user's tasks, with "today" in that user's timezone. */
    public DashboardStatsResponse getStats(UUID ownerId) {
        // "Today" depends on the user's timezone, not on the server's; the definition lives in one place.
        LocalDate today = preferencesService.today(ownerId);

        return new DashboardStatsResponse(
                taskRepository.countByOwnerId(ownerId),
                taskRepository.countByOwnerIdAndStatus(ownerId, TaskStatus.TODO),
                taskRepository.countByOwnerIdAndStatus(ownerId, TaskStatus.IN_PROGRESS),
                taskRepository.countByOwnerIdAndStatus(ownerId, TaskStatus.COMPLETED),
                taskRepository.countOverdue(ownerId, today),
                taskRepository.countDueToday(ownerId, today)
        );
    }
}
