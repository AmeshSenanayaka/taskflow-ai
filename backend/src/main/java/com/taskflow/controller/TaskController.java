package com.taskflow.controller;

import com.taskflow.config.OpenApiConfig;
import com.taskflow.dto.request.CreateTaskRequest;
import com.taskflow.dto.request.UpdateTaskRequest;
import com.taskflow.dto.request.UpdateTaskStatusRequest;
import com.taskflow.dto.response.PageResponse;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.service.TaskService;
import com.taskflow.specification.TaskFilter;
import com.taskflow.specification.TaskSort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@Validated
@Tag(name = OpenApiConfig.TAG_TASKS)
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task", description = "Creates a task. Status starts as TODO; priority defaults to MEDIUM.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request);
    }

    @GetMapping
    @Operation(summary = "List tasks",
            description = "Returns a page of tasks. All filters are optional and combined with AND. "
                    + "Filtering, sorting and pagination are executed in the database.")
    @ApiResponse(responseCode = "200", description = "Page of tasks")
    public PageResponse<TaskResponse> list(
            @Parameter(description = "Filter by one or more statuses (repeat the parameter or use a comma-separated list)",
                    example = "TODO")
            @RequestParam(required = false) List<TaskStatus> status,

            @Parameter(description = "Filter by priority", example = "HIGH")
            @RequestParam(required = false) TaskPriority priority,

            @Parameter(description = "Tasks due exactly on this date (yyyy-MM-dd)", example = "2026-11-02")
            @RequestParam(required = false) LocalDate dueDate,

            @Parameter(description = "Tasks due on or after this date (yyyy-MM-dd)", example = "2026-11-03")
            @RequestParam(required = false) LocalDate dueDateFrom,

            @Parameter(description = "Tasks due on or before this date (yyyy-MM-dd)", example = "2026-11-30")
            @RequestParam(required = false) LocalDate dueDateTo,

            @Parameter(description = "Case-insensitive text matched against title and description", example = "article")
            @RequestParam(required = false) String search,

            @Parameter(description = "Sort field", example = "DUE_DATE")
            @RequestParam(required = false, defaultValue = "CREATED_AT") TaskSort.Field sortBy,

            @Parameter(description = "Sort direction", example = "ASC")
            @RequestParam(required = false, defaultValue = "DESC") TaskSort.Direction sortDirection,

            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size (1-100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        TaskFilter filter = new TaskFilter(status, priority, dueDate, dueDateFrom, dueDateTo, search);
        TaskSort sort = new TaskSort(sortBy, sortDirection);
        return taskService.list(filter, sort, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found"),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TaskResponse getById(@PathVariable UUID id) {
        return taskService.getById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a task", description = "Replaces title, description, priority, status and due date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change task status",
            description = "Use COMPLETED to mark a task done (sets completedAt) or TODO/IN_PROGRESS to reopen it (clears completedAt).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TaskResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateTaskStatusRequest request) {
        return taskService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted"),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void delete(@PathVariable UUID id) {
        taskService.delete(id);
    }
}
