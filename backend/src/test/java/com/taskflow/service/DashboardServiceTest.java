package com.taskflow.service;

import com.taskflow.dto.response.DashboardStatsResponse;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

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
        // 2026-11-02 22:30 UTC is already 2026-11-03 04:00 in Asia/Colombo (UTC+5:30).
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T22:30:00Z"), ZoneOffset.UTC);
        when(preferencesService.getZoneId()).thenReturn(ZoneId.of("Asia/Colombo"));
        LocalDate expectedToday = LocalDate.of(2026, 11, 3);

        when(taskRepository.count()).thenReturn(20L);
        when(taskRepository.countByStatus(TaskStatus.TODO)).thenReturn(8L);
        when(taskRepository.countByStatus(TaskStatus.IN_PROGRESS)).thenReturn(5L);
        when(taskRepository.countByStatus(TaskStatus.COMPLETED)).thenReturn(7L);
        when(taskRepository.countOverdue(expectedToday)).thenReturn(2L);
        when(taskRepository.countDueToday(expectedToday)).thenReturn(3L);

        DashboardStatsResponse stats = new DashboardService(taskRepository, preferencesService, clock).getStats();

        assertThat(stats).isEqualTo(new DashboardStatsResponse(20, 8, 5, 7, 2, 3));
    }

    @Test
    void todayIsResolvedInTheConfiguredTimezone() {
        // Same instant, but a timezone west of UTC is still on 2026-11-02.
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T22:30:00Z"), ZoneOffset.UTC);
        when(preferencesService.getZoneId()).thenReturn(ZoneId.of("America/New_York"));
        LocalDate expectedToday = LocalDate.of(2026, 11, 2);
        when(taskRepository.countOverdue(expectedToday)).thenReturn(1L);
        when(taskRepository.countDueToday(expectedToday)).thenReturn(4L);

        DashboardStatsResponse stats = new DashboardService(taskRepository, preferencesService, clock).getStats();

        assertThat(stats.overdue()).isEqualTo(1);
        assertThat(stats.dueToday()).isEqualTo(4);
    }
}
