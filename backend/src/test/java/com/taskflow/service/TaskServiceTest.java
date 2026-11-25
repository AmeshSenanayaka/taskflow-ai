package com.taskflow.service;

import com.taskflow.dto.request.CreateTaskRequest;
import com.taskflow.dto.request.UpdateTaskRequest;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.TaskNotFoundException;
import com.taskflow.mapper.TaskMapper;
import com.taskflow.repository.TaskRepository;
import com.taskflow.rules.RuleResult;
import com.taskflow.rules.RuleViolationException;
import com.taskflow.rules.task.TaskChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-02T09:30:00Z");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private PreferencesService preferencesService;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        // No rules here: these tests pin down the service's own behaviour. Rules are tested in com.taskflow.rules.
        taskService = new TaskService(taskRepository, new TaskMapper(), fixedClock, preferencesService, List.of());
    }

    @Test
    void createUsesDefaultsForPriorityAndStatus() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(OWNER,
                new CreateTaskRequest("  Write article  ", "   ", null, null, null));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Write article");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(saved.getCompletedAt()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(response.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void createKeepsExplicitPriorityAndDueDate() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate due = LocalDate.of(2026, 11, 10);

        TaskResponse response = taskService.create(OWNER,
                new CreateTaskRequest("Deploy", "Push to prod", TaskPriority.HIGH, due, null));

        assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(response.dueDate()).isEqualTo(due);
        assertThat(response.description()).isEqualTo("Push to prod");
    }

    @Test
    void updatesStampUpdatedAtFromTheClockAndKeepCreatedAt() {
        Task task = newTask();
        Instant originallyCreated = task.getCreatedAt();
        when(taskRepository.findByIdAndOwnerId(task.getId(), OWNER)).thenReturn(Optional.of(task));
        stubSaveAndFlush();

        TaskResponse response = taskService.updateStatus(OWNER, task.getId(), TaskStatus.IN_PROGRESS);

        assertThat(response.createdAt()).isEqualTo(originallyCreated);
        assertThat(response.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void completingATaskSetsCompletedAtFromTheClock() {
        Task task = newTask();
        when(taskRepository.findByIdAndOwnerId(task.getId(), OWNER)).thenReturn(Optional.of(task));
        stubSaveAndFlush();

        TaskResponse response = taskService.updateStatus(OWNER, task.getId(), TaskStatus.COMPLETED);

        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.completedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void reopeningACompletedTaskClearsCompletedAt() {
        Task task = newTask();
        task.changeStatus(TaskStatus.COMPLETED, Instant.parse("2026-11-01T12:00:00Z"));
        when(taskRepository.findByIdAndOwnerId(task.getId(), OWNER)).thenReturn(Optional.of(task));
        stubSaveAndFlush();

        TaskResponse response = taskService.updateStatus(OWNER, task.getId(), TaskStatus.IN_PROGRESS);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.completedAt()).isNull();
    }

    @Test
    void completingAnAlreadyCompletedTaskKeepsOriginalCompletedAt() {
        Task task = newTask();
        Instant originallyCompleted = Instant.parse("2026-11-01T12:00:00Z");
        task.changeStatus(TaskStatus.COMPLETED, originallyCompleted);
        when(taskRepository.findByIdAndOwnerId(task.getId(), OWNER)).thenReturn(Optional.of(task));
        stubSaveAndFlush();

        TaskResponse response = taskService.updateStatus(OWNER, task.getId(), TaskStatus.COMPLETED);

        assertThat(response.completedAt()).isEqualTo(originallyCompleted);
    }

    @Test
    void updateReplacesAllEditableFields() {
        Task task = newTask();
        when(taskRepository.findByIdAndOwnerId(task.getId(), OWNER)).thenReturn(Optional.of(task));
        stubSaveAndFlush();
        LocalDate due = LocalDate.of(2026, 12, 1);

        TaskResponse response = taskService.update(OWNER, task.getId(),
                new UpdateTaskRequest("New title", "New description", TaskPriority.LOW, TaskStatus.COMPLETED, due, null));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isEqualTo("New description");
        assertThat(response.priority()).isEqualTo(TaskPriority.LOW);
        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.dueDate()).isEqualTo(due);
        assertThat(response.completedAt()).isEqualTo(FIXED_NOW);
        // The same managed entity is flushed; no new entity is created.
        verify(taskRepository).saveAndFlush(task);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void getByIdThrowsForUnknownTask() {
        UUID unknown = UUID.randomUUID();
        when(taskRepository.findByIdAndOwnerId(unknown, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getById(OWNER, unknown))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void updateStatusThrowsForUnknownTask() {
        UUID unknown = UUID.randomUUID();
        when(taskRepository.findByIdAndOwnerId(unknown, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateStatus(OWNER, unknown, TaskStatus.COMPLETED))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void deleteThrowsForUnknownTaskAndDoesNotTouchRepository() {
        UUID unknown = UUID.randomUUID();
        when(taskRepository.findByIdAndOwnerId(unknown, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(OWNER, unknown)).isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    void deleteRemovesExistingTask() {
        Task task = newTask();
        when(taskRepository.findByIdAndOwnerId(task.getId(), OWNER)).thenReturn(Optional.of(task));

        taskService.delete(OWNER, task.getId());

        verify(taskRepository).delete(task);
    }

    @Test
    void createAssignsTheTaskToTheCallerPassedByTheController() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taskService.create(OWNER, new CreateTaskRequest("Write article", null, null, null, null));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(OWNER);
    }

    @Test
    void anotherUsersTaskIsReportedAsNotFound() {
        // The repository is asked for (id, caller); a task owned by someone else is simply not returned.
        UUID someoneElsesTask = UUID.randomUUID();
        when(taskRepository.findByIdAndOwnerId(someoneElsesTask, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getById(OWNER, someoneElsesTask))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).findById(any());
    }

    @Test
    void aMissingOwnerIsAProgrammingErrorNotAnUnscopedQuery() {
        assertThatThrownBy(() -> taskService.list(null, com.taskflow.specification.TaskFilter.none(),
                com.taskflow.specification.TaskSort.defaultSort(), 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> taskService.getById(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBrokenRuleStopsTheChangeBeforeAnythingIsWritten() {
        TaskService guarded = new TaskService(taskRepository, new TaskMapper(), Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                preferencesService, List.of(new com.taskflow.rules.DomainRule<TaskChange>() {
                    public String name() {
                        return "ALWAYS_BROKEN";
                    }

                    public RuleResult evaluate(TaskChange change) {
                        return RuleResult.violation("ALWAYS_BROKEN", "title", "Not today.");
                    }
                }));
        Task task = newTask();
        when(taskRepository.findByIdAndOwnerId(task.getId(), OWNER)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> guarded.create(OWNER, new CreateTaskRequest("Anything", null, null, null, null)))
                .isInstanceOf(RuleViolationException.class).hasMessage("Not today.");
        assertThatThrownBy(() -> guarded.updateStatus(OWNER, task.getId(), TaskStatus.COMPLETED))
                .isInstanceOf(RuleViolationException.class);
        verify(taskRepository, never()).save(any());
        verify(taskRepository, never()).saveAndFlush(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void createStoresTheEstimate() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(OWNER, new CreateTaskRequest("Estimate me", null, null, null, 45));

        assertThat(response.estimatedMinutes()).isEqualTo(45);
    }

    private void stubSaveAndFlush() {
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Task newTask() {
        return new Task(OWNER, "Sample", null, TaskPriority.MEDIUM, null, Instant.parse("2026-11-01T08:00:00Z"));
    }
}
