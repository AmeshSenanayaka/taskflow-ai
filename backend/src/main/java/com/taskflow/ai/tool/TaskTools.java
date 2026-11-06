package com.taskflow.ai.tool;

import com.taskflow.ai.exception.ToolExecutionException;
import com.taskflow.dto.request.CreateTaskRequest;
import com.taskflow.dto.response.PageResponse;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.TaskNotFoundException;
import com.taskflow.service.TaskService;
import com.taskflow.specification.TaskFilter;
import com.taskflow.specification.TaskSort;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTools {

    private final TaskService taskService;

    @Tool("Create a new task with a title, description, priority and due date")
    public TaskResponse createTask(
            @P("Task title") String title,
            @P(value = "Task description", required = false) String description,
            @P(value = "Priority: LOW, MEDIUM or HIGH (defaults to MEDIUM)", required = false) String priority,
            @P(value = "Due date in YYYY-MM-DD format, or null", required = false) String dueDate
    ) {
        return guarded("createTask", () -> {
            if (title == null || title.isBlank()) {
                throw new ToolExecutionException("A task title is required. Ask the user what the task is called.");
            }
            CreateTaskRequest request = new CreateTaskRequest(
                    title.trim(),
                    description,
                    parseEnum(TaskPriority.class, priority, TaskPriority.MEDIUM),
                    parseDate(dueDate)
            );
            return taskService.create(request);
        });
    }

    @Tool("Get a task by its unique ID")
    public TaskResponse getTaskById(
            @P("Unique task ID") UUID taskId
    ) {
        return guarded("getTaskById", () -> {
            if (taskId == null) {
                throw new ToolExecutionException("A task id is required. Use listTasks to find the task first.");
            }
            return taskService.getById(taskId);
        });
    }


    @Tool("""
        Search and list tasks using filters, sorting, and pagination.
        Use this tool to find tasks by status, priority,
        due date, date range, or keyword.
        Use ISO dates (YYYY-MM-DD).
        Page numbering starts at 0.
        """)
    public PageResponse<TaskResponse> listTasks(
            @P("Task statuses, e.g. TODO, IN_PROGRESS, COMPLETED. Null for all statuses")
            List<TaskStatus> statuses,

            @P(value = "Task priority: LOW, MEDIUM or HIGH. Null for all priorities", required = false)
            TaskPriority priority,

            @P("Exact due date in YYYY-MM-DD format, or null")
            String dueDate,

            @P("Due date range start in YYYY-MM-DD format, or null")
            String dueDateFrom,

            @P("Due date range end in YYYY-MM-DD format, or null")
            String dueDateTo,

            @P("Search keyword, or null")
            String search,

            @P(value = "Sort field: DUE_DATE, PRIORITY, CREATED_AT or UPDATED_AT (default CREATED_AT)", required = false)
            String sortBy,

            @P(value = "Sort direction: ASC or DESC (default DESC)", required = false)
            String direction,

            @P("Page number, starting from 0")
            int page,

            @P("Page size, maximum 50")
            int size
    ) {
        return guarded("listTasks", () -> {
            TaskFilter filter = new TaskFilter(
                    statuses,
                    priority,
                    parseDate(dueDate),
                    parseDate(dueDateFrom),
                    parseDate(dueDateTo),
                    search
            );
            TaskSort sort = createTaskSort(sortBy, direction);
            return taskService.list(
                    filter,
                    sort,
                    Math.max(0, page),
                    Math.clamp(size, 1, 50)
            );
        });
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ToolExecutionException("Invalid date '" + value + "'. Dates must be absolute and in yyyy-MM-dd "
                    + "format; resolve words like 'tomorrow' against today's date first.");
        }
    }

    /**
     * Runs a tool and converts every failure into a message the model can act on.
     * <p>
     * LangChain4j returns the exception message to the model as the tool result, so expected problems (unknown task,
     * bad date, validation) are phrased as instructions, while anything unexpected is logged with the stack trace and
     * replaced by a generic message so no SQL, hostnames or internal details reach the model or the user.
     */
    private <T> T guarded(String toolName, Supplier<T> action) {
        try {
            return action.get();
        } catch (ToolExecutionException ex) {
            log.info("Tool {} rejected the request: {}", toolName, ex.getMessage());
            throw ex;
        } catch (TaskNotFoundException ex) {
            log.info("Tool {}: {}", toolName, ex.getMessage());
            throw new ToolExecutionException(ex.getMessage() + ". Use listTasks to find the correct task id.", ex);
        } catch (ConstraintViolationException ex) {
            String problems = ex.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; "));
            log.info("Tool {} failed validation: {}", toolName, problems);
            throw new ToolExecutionException("Validation failed: " + problems + ". Fix the arguments and try again.", ex);
        } catch (IllegalArgumentException ex) {
            log.info("Tool {} received a bad argument: {}", toolName, ex.getMessage());
            throw new ToolExecutionException("Invalid argument: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.error("Tool {} failed unexpectedly", toolName, ex);
            throw new ToolExecutionException("The " + toolName + " tool failed because of an internal problem. "
                    + "Tell the user and suggest trying again later.", ex);
        }
    }

    /**
     * Maps the model's free-text sort arguments onto {@link TaskSort}. Anything missing or unrecognised
     * falls back to the API default (newest first) instead of failing the tool call.
     */
    private TaskSort createTaskSort(String sortBy, String direction) {
        TaskSort.Field field = parseEnum(TaskSort.Field.class, sortBy, TaskSort.Field.CREATED_AT);
        TaskSort.Direction dir = parseEnum(TaskSort.Direction.class, direction, TaskSort.Direction.DESC);
        return new TaskSort(field, dir);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
