package com.taskflow.ai.tool;

import com.taskflow.ai.exception.ToolExecutionException;
import com.taskflow.dto.request.CreateTaskRequest;
import com.taskflow.dto.request.UpdateTaskRequest;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.TaskService;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskToolsTest {

    @Mock
    private TaskService taskService;

    @Mock
    private PreferencesService preferencesService;

    @Mock
    private CurrentUserProvider currentUser;

    private TaskTools taskTools;

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

    @BeforeEach
    void signIn() {
        taskTools = new TaskTools(taskService, preferencesService, currentUser,
                new com.taskflow.ai.guardrail.GuardrailProperties(true, 10, 1));
        lenient().when(currentUser.userId()).thenReturn(USER);
    }

    @Test
    void getTodayReturnsTheSharedUserDateAndTimezone() {
        when(preferencesService.today(USER)).thenReturn(LocalDate.of(2026, 11, 3));
        when(preferencesService.getZoneId(USER)).thenReturn(ZoneId.of("Asia/Colombo"));

        TaskTools.TodayResponse today = taskTools.getToday();

        assertThat(today.today()).isEqualTo("2026-11-03");
        assertThat(today.timezone()).isEqualTo("Asia/Colombo");
    }

    @Test
    void createTaskPassesTheDueDateThroughAsACalendarDay() {
        taskTools.createTask("Deploy", null, "HIGH", "2026-11-15", null);

        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskService).create(eq(USER), captor.capture());
        assertThat(captor.getValue().dueDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void relativeDatesAreRejectedWithAPointerToGetToday() {
        assertThatThrownBy(() -> taskTools.createTask("Deploy", null, null, "tomorrow", null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("getToday");
    }

    @Test
    void updateTaskChangesOnlyTheFieldsThatWerePassed() {
        UUID id = UUID.randomUUID();
        UpdateTaskRequest current = new UpdateTaskRequest("Write the article", "Draft", TaskPriority.MEDIUM,
                TaskStatus.TODO, LocalDate.of(2026, 11, 20), null);
        AtomicReference<UpdateTaskRequest> stored = new AtomicReference<>();
        when(taskService.patch(eq(USER), eq(id), any())).thenAnswer(invocation -> {
            UnaryOperator<UpdateTaskRequest> change = invocation.getArgument(2);
            stored.set(change.apply(current));
            return null;
        });

        // priority in lower case, empty dueDate = clear it, everything else untouched
        taskTools.updateTask(id, null, null, "low", null, "", null);

        assertThat(stored.get()).isEqualTo(new UpdateTaskRequest("Write the article", "Draft", TaskPriority.LOW,
                TaskStatus.TODO, null, null));
    }

    @Test
    void updateTaskRejectsAnUnknownPriorityInsteadOfSilentlyKeepingTheOldOne() {
        assertThatThrownBy(() -> taskTools.updateTask(UUID.randomUUID(), null, null, "URGENT", null, null, null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Invalid priority 'URGENT'")
                .hasMessageContaining("Nothing was changed");
        verifyNoInteractions(taskService);
    }

    @Test
    void updateTaskRejectsAnUnknownStatusAndABlankTitle() {
        assertThatThrownBy(() -> taskTools.updateTask(UUID.randomUUID(), null, null, null, "DONE", null, null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Invalid status 'DONE'");
        assertThatThrownBy(() -> taskTools.updateTask(UUID.randomUUID(), "   ", null, null, null, null, null))
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
    @Test
    void toolsActForTheAuthenticatedUserNeverForAnIdFromTheModel() {
        UUID taskId = UUID.randomUUID();

        taskTools.getTaskById(taskId);
        taskTools.updateTaskStatus(taskId, "COMPLETED");

        verify(taskService).getById(USER, taskId);
        verify(taskService).updateStatus(USER, taskId, TaskStatus.COMPLETED);
    }

    @Test
    void noToolLetsTheModelChooseTheUser() {
        // The owner comes from CurrentUserProvider only; a tool parameter for it would let a prompt injection pick it.
        assertThat(Arrays.stream(TaskTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .map(Parameter::getName)
                .map(String::toLowerCase))
                .noneMatch(name -> name.contains("user") || name.contains("owner"));
    }

    @Test
    void withoutAnAuthenticatedUserNoDataIsTouched() {
        when(currentUser.userId()).thenThrow(new AuthenticationCredentialsNotFoundException("no user"));

        assertThatThrownBy(() -> taskTools.getTaskById(UUID.randomUUID()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("no signed-in user");
        verifyNoInteractions(taskService);
    }

    @Test
    void aMessageCanChangeAtMostTheConfiguredNumberOfTasks() {
        TaskTools limited = new TaskTools(taskService, preferencesService, currentUser,
                new com.taskflow.ai.guardrail.GuardrailProperties(true, 2, 1));
        com.taskflow.ai.orchestration.AgentRunRecorder.Run run = com.taskflow.ai.orchestration.AgentRunRecorder.open();
        try {
            limited.updateTaskStatus(UUID.randomUUID(), "COMPLETED");
            limited.updateTaskStatus(UUID.randomUUID(), "COMPLETED");

            assertThatThrownBy(() -> limited.updateTaskStatus(UUID.randomUUID(), "COMPLETED"))
                    .isInstanceOf(ToolExecutionException.class)
                    .hasMessageContaining("at most 2 tasks");
            assertThat(run.guardrails()).containsExactly(com.taskflow.ai.guardrail.GuardrailCodes.TOOL_WRITE_LIMIT);
        } finally {
            com.taskflow.ai.orchestration.AgentRunRecorder.close();
        }
    }
}
