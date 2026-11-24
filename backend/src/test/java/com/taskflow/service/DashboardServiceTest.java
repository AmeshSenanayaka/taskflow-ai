package com.taskflow.service;

import com.taskflow.dto.response.DashboardStatsResponse;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private PreferencesService preferencesService;

    @Test
    void statsAreAggregatedFromRepositoryCounts() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        when(preferencesService.today(OWNER)).thenReturn(today);

        when(taskRepository.countByOwnerId(OWNER)).thenReturn(20L);
        when(taskRepository.countByOwnerIdAndStatus(OWNER, TaskStatus.TODO)).thenReturn(8L);
        when(taskRepository.countByOwnerIdAndStatus(OWNER, TaskStatus.IN_PROGRESS)).thenReturn(5L);
        when(taskRepository.countByOwnerIdAndStatus(OWNER, TaskStatus.COMPLETED)).thenReturn(7L);
        when(taskRepository.countOverdue(OWNER, today)).thenReturn(2L);
        when(taskRepository.countDueToday(OWNER, today)).thenReturn(3L);

        DashboardStatsResponse stats = new DashboardService(taskRepository, preferencesService).getStats(OWNER);

        assertThat(stats).isEqualTo(new DashboardStatsResponse(20, 8, 5, 7, 2, 3));
    }

    @Test
    void overdueAndDueTodayUseTheUsersToday() {
        // The dashboard must not compute its own "today"; the shared definition in PreferencesService wins.
        LocalDate usersToday = LocalDate.of(2026, 11, 2);
        when(preferencesService.today(OWNER)).thenReturn(usersToday);
        when(taskRepository.countOverdue(OWNER, usersToday)).thenReturn(1L);
        when(taskRepository.countDueToday(OWNER, usersToday)).thenReturn(4L);

        DashboardStatsResponse stats = new DashboardService(taskRepository, preferencesService).getStats(OWNER);

        assertThat(stats.overdue()).isEqualTo(1);
        assertThat(stats.dueToday()).isEqualTo(4);
    }
}
