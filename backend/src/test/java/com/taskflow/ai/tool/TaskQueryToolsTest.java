package com.taskflow.ai.tool;

import com.taskflow.ai.exception.ToolExecutionException;
import com.taskflow.dto.response.PageResponse;
import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.service.DashboardService;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.TaskService;
import com.taskflow.specification.TaskFilter;
import com.taskflow.specification.TaskSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskQueryToolsTest {

    // 2026-11-08T20:30Z is already 2026-11-09 02:00 in Asia/Colombo (UTC+5:30).
    private static final Instant NOW = Instant.parse("2026-11-08T20:30:00Z");

    @Mock
    private TaskService taskService;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private PreferencesService preferencesService;

    private TaskQueryTools tools;

    @BeforeEach
    void setUp() {
        tools = new TaskQueryTools(taskService, dashboardService, preferencesService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void planningContextUsesTheUsersTimezoneAndPreferences() {
        when(preferencesService.getZoneId()).thenReturn(ZoneId.of("Asia/Colombo"));
        when(preferencesService.get()).thenReturn(new PreferencesResponse("Asia/Colombo", 360, NOW));

        TaskQueryTools.PlanningContext context = tools.getPlanningContext();

        assertThat(context.today()).isEqualTo("2026-11-09");
        assertThat(context.dayOfWeek()).isEqualTo("Monday");
        assertThat(context.timezone()).isEqualTo("Asia/Colombo");
        assertThat(context.dailyAvailableMinutes()).isEqualTo(360);
    }

    @Test
    void findTasksMapsFiltersSortAndLimitAndReportsTruncation() {
        TaskResponse task = new TaskResponse(UUID.randomUUID(), "A", null, TaskPriority.HIGH, TaskStatus.TODO,
                LocalDate.parse("2026-11-10"), NOW, NOW, null);
        when(taskService.list(any(), any(), eq(0), eq(5)))
                .thenReturn(new PageResponse<>(List.of(task), 0, 5, 12, 3, true, false));

        TaskQueryTools.TaskSearchResult result = tools.findTasks(
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS), TaskPriority.HIGH,
                "2026-11-01", "2026-11-30", "arch", "priority", "desc", 5);

        assertThat(result.tasks()).containsExactly(task);
        assertThat(result.totalMatches()).isEqualTo(12);
        assertThat(result.truncated()).isTrue();
        ArgumentCaptor<TaskFilter> filter = ArgumentCaptor.forClass(TaskFilter.class);
        ArgumentCaptor<TaskSort> sort = ArgumentCaptor.forClass(TaskSort.class);
        verify(taskService).list(filter.capture(), sort.capture(), eq(0), eq(5));
        assertThat(filter.getValue()).isEqualTo(new TaskFilter(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                TaskPriority.HIGH, null, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), "arch"));
        assertThat(sort.getValue()).isEqualTo(new TaskSort(TaskSort.Field.PRIORITY, TaskSort.Direction.DESC));
    }

    @Test
    void findTasksDefaultsToDueDateAscendingAndClampsTheLimit() {
        when(taskService.list(any(), any(), eq(0), eq(50)))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0, true, true));

        TaskQueryTools.TaskSearchResult result = tools.findTasks(null, null, null, null, null, null, null, 500);

        assertThat(result.tasks()).isEmpty();
        assertThat(result.truncated()).isFalse();
        verify(taskService).list(eq(TaskFilter.none()), eq(new TaskSort(TaskSort.Field.DUE_DATE, TaskSort.Direction.ASC)),
                eq(0), eq(50));
    }

    @Test
    void relativeDatesAreRejectedWithAHintForTheModel() {
        assertThatThrownBy(() -> tools.findTasks(null, null, "tomorrow", null, null, null, null, null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("yyyy-MM-dd")
                .hasMessageContaining("getPlanningContext");
    }

    @Test
    void unexpectedFailuresAreHiddenBehindAGenericMessage() {
        when(dashboardService.getStats()).thenThrow(new IllegalStateException("connection to db-host:5432 refused"));

        assertThatThrownBy(tools::getTaskStatistics)
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("getTaskStatistics tool failed because of an internal problem")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("db-host"));
    }
}
