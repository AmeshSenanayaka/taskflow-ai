package com.taskflow.service;

import com.taskflow.dto.response.DashboardStatsResponse;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private PreferencesService preferencesService;

    @Test
    void statsAreAggregatedFromRepositoryCounts() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        when(preferencesService.today()).thenReturn(today);

        when(taskRepository.count()).thenReturn(20L);
        when(taskRepository.countByStatus(TaskStatus.TODO)).thenReturn(8L);
        when(taskRepository.countByStatus(TaskStatus.IN_PROGRESS)).thenReturn(5L);
        when(taskRepository.countByStatus(TaskStatus.COMPLETED)).thenReturn(7L);
        when(taskRepository.countOverdue(today)).thenReturn(2L);
        when(taskRepository.countDueToday(today)).thenReturn(3L);

        DashboardStatsResponse stats = new DashboardService(taskRepository, preferencesService).getStats();

        assertThat(stats).isEqualTo(new DashboardStatsResponse(20, 8, 5, 7, 2, 3));
    }

    @Test
    void overdueAndDueTodayUseTheUsersToday() {
        // The dashboard must not compute its own "today"; the shared definition in PreferencesService wins.
        LocalDate usersToday = LocalDate.of(2026, 11, 2);
        when(preferencesService.today()).thenReturn(usersToday);
        when(taskRepository.countOverdue(usersToday)).thenReturn(1L);
        when(taskRepository.countDueToday(usersToday)).thenReturn(4L);

        DashboardStatsResponse stats = new DashboardService(taskRepository, preferencesService).getStats();

        assertThat(stats.overdue()).isEqualTo(1);
        assertThat(stats.dueToday()).isEqualTo(4);
    }
}
