package com.taskflow.ai.tool;

import com.taskflow.ai.exception.ToolExecutionException;
import com.taskflow.dto.request.CreateTaskRequest;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskToolsTest {

    @Mock
    private TaskService taskService;

    @Mock
    private PreferencesService preferencesService;

    @InjectMocks
    private TaskTools taskTools;

    @Test
    void getTodayReturnsTheSharedUserDateAndTimezone() {
        when(preferencesService.today()).thenReturn(LocalDate.of(2026, 11, 3));
        when(preferencesService.getZoneId()).thenReturn(ZoneId.of("Asia/Colombo"));

        TaskTools.TodayResponse today = taskTools.getToday();

        assertThat(today.today()).isEqualTo("2026-11-03");
        assertThat(today.timezone()).isEqualTo("Asia/Colombo");
    }

    @Test
    void createTaskPassesTheDueDateThroughAsACalendarDay() {
        taskTools.createTask("Deploy", null, "HIGH", "2026-11-15");

        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskService).create(captor.capture());
        assertThat(captor.getValue().dueDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void relativeDatesAreRejectedWithAPointerToGetToday() {
        assertThatThrownBy(() -> taskTools.createTask("Deploy", null, null, "tomorrow"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("getToday");
    }
}
