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

    @Mock
    private TaskRepository taskRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        taskService = new TaskService(taskRepository, new TaskMapper(), fixedClock);
    }

    @Test
    void createUsesDefaultsForPriorityAndStatus() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(
                new CreateTaskRequest("  Write article  ", "   ", null, null));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Write article");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(saved.getCompletedAt()).isNull();
        assertThat(response.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void createKeepsExplicitPriorityAndDueDate() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate due = LocalDate.of(2026, 11, 10);

        TaskResponse response = taskService.create(
                new CreateTaskRequest("Deploy", "Push to prod", TaskPriority.HIGH, due));

        assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(response.dueDate()).isEqualTo(due);
        assertThat(response.description()).isEqualTo("Push to prod");
    }

    @Test
    void completingATaskSetsCompletedAtFromTheClock() {
        Task task = newTask();
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        stubSaveAndFlush();

        TaskResponse response = taskService.updateStatus(task.getId(), TaskStatus.COMPLETED);

        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.completedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void reopeningACompletedTaskClearsCompletedAt() {
        Task task = newTask();
        task.changeStatus(TaskStatus.COMPLETED, Instant.parse("2026-11-01T12:00:00Z"));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        stubSaveAndFlush();

        TaskResponse response = taskService.updateStatus(task.getId(), TaskStatus.IN_PROGRESS);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.completedAt()).isNull();
    }

    @Test
    void completingAnAlreadyCompletedTaskKeepsOriginalCompletedAt() {
        Task task = newTask();
        Instant originallyCompleted = Instant.parse("2026-11-01T12:00:00Z");
        task.changeStatus(TaskStatus.COMPLETED, originallyCompleted);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        stubSaveAndFlush();

        TaskResponse response = taskService.updateStatus(task.getId(), TaskStatus.COMPLETED);

        assertThat(response.completedAt()).isEqualTo(originallyCompleted);
    }

    @Test
    void updateReplacesAllEditableFields() {
        Task task = newTask();
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        stubSaveAndFlush();
        LocalDate due = LocalDate.of(2026, 12, 1);

        TaskResponse response = taskService.update(task.getId(),
                new UpdateTaskRequest("New title", "New description", TaskPriority.LOW, TaskStatus.COMPLETED, due));

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
        when(taskRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getById(unknown))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void updateStatusThrowsForUnknownTask() {
        UUID unknown = UUID.randomUUID();
        when(taskRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateStatus(unknown, TaskStatus.COMPLETED))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void deleteThrowsForUnknownTaskAndDoesNotTouchRepository() {
        UUID unknown = UUID.randomUUID();
        when(taskRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(unknown)).isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    void deleteRemovesExistingTask() {
        Task task = newTask();
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        taskService.delete(task.getId());

        verify(taskRepository).delete(task);
    }

    private void stubSaveAndFlush() {
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Task newTask() {
        return new Task("Sample", null, TaskPriority.MEDIUM, null);
    }
}
