package com.taskflow.ai.tool;

import com.taskflow.ai.exception.ToolExecutionException;
import com.taskflow.dto.request.CreateTaskRequest;
import com.taskflow.dto.request.UpdateTaskRequest;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.TaskService;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void updateTaskChangesOnlyTheFieldsThatWerePassed() {
        UUID id = UUID.randomUUID();
        UpdateTaskRequest current = new UpdateTaskRequest("Write the article", "Draft", TaskPriority.MEDIUM,
                TaskStatus.TODO, LocalDate.of(2026, 11, 20));
        AtomicReference<UpdateTaskRequest> stored = new AtomicReference<>();
        when(taskService.patch(eq(id), any())).thenAnswer(invocation -> {
            UnaryOperator<UpdateTaskRequest> change = invocation.getArgument(1);
            stored.set(change.apply(current));
            return null;
        });

        // priority in lower case, empty dueDate = clear it, everything else untouched
        taskTools.updateTask(id, null, null, "low", null, "");

        assertThat(stored.get()).isEqualTo(new UpdateTaskRequest("Write the article", "Draft", TaskPriority.LOW,
                TaskStatus.TODO, null));
    }

    @Test
    void updateTaskRejectsAnUnknownPriorityInsteadOfSilentlyKeepingTheOldOne() {
        assertThatThrownBy(() -> taskTools.updateTask(UUID.randomUUID(), null, null, "URGENT", null, null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Invalid priority 'URGENT'")
                .hasMessageContaining("Nothing was changed");
        verifyNoInteractions(taskService);
    }

    @Test
    void updateTaskRejectsAnUnknownStatusAndABlankTitle() {
        assertThatThrownBy(() -> taskTools.updateTask(UUID.randomUUID(), null, null, null, "DONE", null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Invalid status 'DONE'");
        assertThatThrownBy(() -> taskTools.updateTask(UUID.randomUUID(), "   ", null, null, null, null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("title cannot be blank");
        verifyNoInteractions(taskService);
    }

    @Test
    void everyToolThatChangesTasksIsMarkedAsMutating() {
        // Read tools are named get* / list*; anything else must carry @MutatesTasks, otherwise the supervisor
        // would report dataChanged = false after it ran and the UI would show stale lists.
        assertThat(Arrays.stream(TaskTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .filter(method -> !method.isAnnotationPresent(MutatesTasks.class))
                .map(Method::getName)
                .filter(name -> !name.startsWith("get") && !name.startsWith("list")))
                .isEmpty();
    }
}
