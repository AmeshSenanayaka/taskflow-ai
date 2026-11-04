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
import java.util.UUID;

/**
 * All task business rules live here. Controllers only translate HTTP to these calls,
 * which is what will let future AI tools (Phase 1+) reuse the same logic without HTTP.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, TaskMapper taskMapper, Clock clock) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        TaskPriority priority = request.priority() != null ? request.priority() : TaskPriority.MEDIUM;
        Task task = new Task(request.title().trim(), normalize(request.description()), priority, request.dueDate());
        return taskMapper.toResponse(taskRepository.save(task));
    }

    public TaskResponse getById(UUID id) {
        return taskMapper.toResponse(findOrThrow(id));
    }

    public PageResponse<TaskResponse> list(TaskFilter filter, TaskSort sort, int page, int size) {
        // Sorting is applied inside the Specification (priority needs a CASE expression),
        // so the Pageable intentionally carries no Sort of its own.
        Pageable pageable = PageRequest.of(page, size);
        Specification<Task> spec = TaskSpecifications.withFilter(filter)
                .and(TaskSpecifications.sortedBy(sort));
        Page<TaskResponse> result = taskRepository.findAll(spec, pageable).map(taskMapper::toResponse);
        return PageResponse.from(result);
    }

    @Transactional
    public TaskResponse update(UUID id, UpdateTaskRequest request) {
        Task task = findOrThrow(id);
        task.setTitle(request.title().trim());
        task.setDescription(normalize(request.description()));
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        task.changeStatus(request.status(), now());
        // Flush so @PreUpdate runs and the response carries the fresh updatedAt.
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public TaskResponse updateStatus(UUID id, TaskStatus status) {
        Task task = findOrThrow(id);
        task.changeStatus(status, now());
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void delete(UUID id) {
        Task task = findOrThrow(id);
        taskRepository.delete(task);
    }

    private Task findOrThrow(UUID id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
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
