package com.taskflow.service;

import com.taskflow.dto.request.CreateTaskRequest;
import com.taskflow.dto.request.UpdateTaskRequest;
import com.taskflow.dto.response.PageResponse;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.TaskNotFoundException;
import com.taskflow.mapper.TaskMapper;
import com.taskflow.repository.TaskRepository;
import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleEngine;
import com.taskflow.rules.task.TaskChange;
import com.taskflow.specification.TaskFilter;
import com.taskflow.specification.TaskSort;
import com.taskflow.specification.TaskSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * All task business rules live here. Controllers only translate HTTP to these calls,
 * which is what lets the AI tools (Phase 1+) reuse the same logic without HTTP.
 * <p>
 * Phase 3.0: every method takes the owner's id as its first argument and every query is scoped by it.
 * The caller supplies that id from the authenticated identity ({@code CurrentUserProvider}); this service
 * never reads it from request data. Another user's task behaves exactly like a task that does not exist
 * ({@link TaskNotFoundException}, HTTP 404), so its existence is not revealed.
 * <p>
 * Phase 3.2: every change (create, update, partial update, status change) is checked against the task rules
 * ({@code DomainRule<TaskChange>} beans) before anything is written. The UI, the REST API and the AI tools all come
 * through here, so they all get the same rules; a broken rule throws {@code RuleViolationException}.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final Clock clock;
    private final PreferencesService preferencesService;
    private final List<DomainRule<TaskChange>> taskRules;

    public TaskService(TaskRepository taskRepository, TaskMapper taskMapper, Clock clock,
                       PreferencesService preferencesService, List<DomainRule<TaskChange>> taskRules) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.clock = clock;
        this.preferencesService = preferencesService;
        this.taskRules = List.copyOf(taskRules);
    }

    @Transactional
    public TaskResponse create(UUID ownerId, CreateTaskRequest request) {
        TaskPriority priority = request.priority() != null ? request.priority() : TaskPriority.MEDIUM;
        String title = request.title().trim();
        checkRules(new TaskChange(requireOwner(ownerId), null, null,
                new TaskChange.State(title, priority, TaskStatus.TODO, request.dueDate()), today(ownerId)));
        Task task = new Task(ownerId, title, normalize(request.description()), priority, request.dueDate(), now());
        task.setEstimatedMinutes(request.estimatedMinutes());
        return taskMapper.toResponse(taskRepository.save(task));
    }

    public TaskResponse getById(UUID ownerId, UUID id) {
        return taskMapper.toResponse(findOrThrow(ownerId, id));
    }

    public PageResponse<TaskResponse> list(UUID ownerId, TaskFilter filter, TaskSort sort, int page, int size) {
        // Sorting is applied inside the Specification (priority needs a CASE expression),
        // so the Pageable intentionally carries no Sort of its own.
        Pageable pageable = PageRequest.of(page, size);
        Specification<Task> spec = TaskSpecifications.ownedBy(requireOwner(ownerId))
                .and(TaskSpecifications.withFilter(filter))
                .and(TaskSpecifications.sortedBy(sort));
        Page<TaskResponse> result = taskRepository.findAll(spec, pageable).map(taskMapper::toResponse);
        return PageResponse.from(result);
    }

    @Transactional
    public TaskResponse update(UUID ownerId, UUID id, UpdateTaskRequest request) {
        return apply(findOrThrow(ownerId, id), request);
    }

    /**
     * Partial update: {@code change} receives the task's current values and returns the values to store.
     * The row is locked for the duration of the transaction, so the read and the write cannot be separated
     * by another writer (the AI {@code updateTask} tool changes one field and must not overwrite the others
     * with values it read earlier).
     */
    @Transactional
    public TaskResponse patch(UUID ownerId, UUID id, UnaryOperator<UpdateTaskRequest> change) {
        Task task = taskRepository.findByIdAndOwnerIdForUpdate(id, requireOwner(ownerId))
                .orElseThrow(() -> new TaskNotFoundException(id));
        UpdateTaskRequest current = new UpdateTaskRequest(task.getTitle(), task.getDescription(), task.getPriority(),
                task.getStatus(), task.getDueDate(), task.getEstimatedMinutes());
        return apply(task, change.apply(current));
    }

    private TaskResponse apply(Task task, UpdateTaskRequest request) {
        String title = request.title().trim();
        checkRules(new TaskChange(task.getOwnerId(), task.getId(), TaskChange.State.of(task),
                new TaskChange.State(title, request.priority(), request.status(), request.dueDate()),
                today(task.getOwnerId())));
        task.setTitle(title);
        task.setDescription(normalize(request.description()));
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        task.setEstimatedMinutes(request.estimatedMinutes());
        task.changeStatus(request.status(), now());
        task.touch(now());
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public TaskResponse updateStatus(UUID ownerId, UUID id, TaskStatus status) {
        Task task = findOrThrow(ownerId, id);
        checkRules(new TaskChange(ownerId, id, TaskChange.State.of(task),
                new TaskChange.State(task.getTitle(), task.getPriority(), status, task.getDueDate()), today(ownerId)));
        task.changeStatus(status, now());
        task.touch(now());
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Task task = findOrThrow(ownerId, id);
        taskRepository.delete(task);
    }

    private Task findOrThrow(UUID ownerId, UUID id) {
        return taskRepository.findByIdAndOwnerId(id, requireOwner(ownerId))
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private void checkRules(TaskChange change) {
        RuleEngine.enforce(taskRules, change);
    }

    private java.time.LocalDate today(UUID ownerId) {
        return preferencesService.today(ownerId);
    }

    /** A missing owner is a programming error; failing loudly beats running an unscoped query. */
    private static UUID requireOwner(UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId is required");
        }
        return ownerId;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static String normalize(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
