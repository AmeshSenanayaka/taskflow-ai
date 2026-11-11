package com.taskflow.ai.tool;

import com.taskflow.ai.exception.ToolExecutionException;
import com.taskflow.dto.response.DashboardStatsResponse;
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
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Read-only tools for the analytical agents of Phase 2 ({@code PriorityAgent}, {@code PlanningAgent},
 * {@code SummaryAgent}). Keeping them in a separate class from {@link TaskTools} is what makes those agents
 * read-only by construction: they are never given a tool that can create or change a task, so no prompt
 * injection or model mistake can turn an analysis into a mutation.
 */
@Slf4j
@Component
public class TaskQueryTools {

    static final int MAX_RESULTS = 50;

    private final TaskService taskService;
    private final DashboardService dashboardService;
    private final PreferencesService preferencesService;
    private final Clock clock;

    public TaskQueryTools(TaskService taskService,
                          DashboardService dashboardService,
                          PreferencesService preferencesService,
                          Clock clock) {
        this.taskService = taskService;
        this.dashboardService = dashboardService;
        this.preferencesService = preferencesService;
        this.clock = clock;
    }

    /** What every analytical agent needs before reasoning about "today", "overdue" or "how much fits in a day". */
    public record PlanningContext(
            String today,
            String dayOfWeek,
            String timezone,
            Integer dailyAvailableMinutes
    ) {
    }

    @Tool("""
            Returns today's date (YYYY-MM-DD), the day of the week, the user's timezone and the number of
            minutes the user has available for tasks per day. Call it first to resolve words like
            'today', 'tomorrow' or 'overdue' and to know how much work fits in a day.
            """)
    public PlanningContext getPlanningContext() {
        return guarded("getPlanningContext", () -> {
            PreferencesResponse preferences = preferencesService.get();
            ZoneId zone = preferencesService.getZoneId();
            LocalDate today = LocalDate.now(clock.withZone(zone));
            DayOfWeek dayOfWeek = today.getDayOfWeek();
            return new PlanningContext(
                    today.toString(),
                    dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    zone.getId(),
                    preferences.dailyAvailableMinutes()
            );
        });
    }

    @Tool("Returns task counts: total, pending (TODO), inProgress, completed, overdue and dueToday")
    public DashboardStatsResponse getTaskStatistics() {
        return guarded("getTaskStatistics", dashboardService::getStats);
    }

    @Tool("""
            Finds tasks (read-only). Filter by statuses, priority, due date range or keyword, choose the sort
            order and limit the number of results (maximum 50). Dates use YYYY-MM-DD. Returns the matching
            tasks plus the total number of matches.
            """)
    public TaskSearchResult findTasks(
            @P(value = "Statuses to include, e.g. TODO, IN_PROGRESS, COMPLETED. Null for all", required = false)
            List<TaskStatus> statuses,
            @P(value = "Priority: LOW, MEDIUM or HIGH. Null for all", required = false)
            TaskPriority priority,
            @P(value = "Earliest due date (inclusive) in YYYY-MM-DD, or null", required = false)
            String dueDateFrom,
            @P(value = "Latest due date (inclusive) in YYYY-MM-DD, or null", required = false)
            String dueDateTo,
            @P(value = "Keyword matched against title and description, or null", required = false)
            String search,
            @P(value = "Sort field: DUE_DATE, PRIORITY, CREATED_AT or UPDATED_AT (default DUE_DATE)", required = false)
            String sortBy,
            @P(value = "Sort direction: ASC or DESC (default ASC)", required = false)
            String direction,
            @P(value = "Maximum number of tasks to return, 1-50 (default 20)", required = false)
            Integer limit
    ) {
        return guarded("findTasks", () -> {
            TaskFilter filter = new TaskFilter(
                    statuses == null || statuses.isEmpty() ? null : statuses,
                    priority,
                    null,
                    parseDate(dueDateFrom),
                    parseDate(dueDateTo),
                    search
            );
            TaskSort sort = new TaskSort(
                    parseEnum(TaskSort.Field.class, sortBy, TaskSort.Field.DUE_DATE),
                    parseEnum(TaskSort.Direction.class, direction, TaskSort.Direction.ASC));
            int size = limit == null ? 20 : Math.clamp(limit, 1, MAX_RESULTS);
            PageResponse<TaskResponse> page = taskService.list(filter, sort, 0, size);
            return new TaskSearchResult(page.content(), page.totalElements(), page.totalElements() > page.content().size());
        });
    }

    /** Flat result for the model: the tasks, how many matched in total and whether the list was cut. */
    public record TaskSearchResult(List<TaskResponse> tasks, long totalMatches, boolean truncated) {
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ToolExecutionException("Invalid date '" + value + "'. Dates must be absolute and in yyyy-MM-dd "
                    + "format; call getPlanningContext to learn today's date first.");
        }
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

    private <T> T guarded(String toolName, Supplier<T> action) {
        try {
            return action.get();
        } catch (ToolExecutionException ex) {
            log.info("Tool {} rejected the request: {}", toolName, ex.getMessage());
            throw ex;
        } catch (IllegalArgumentException ex) {
            log.info("Tool {} received a bad argument: {}", toolName, ex.getMessage());
            throw new ToolExecutionException("Invalid argument: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.error("Tool {} failed unexpectedly", toolName, ex);
            throw new ToolExecutionException("The " + toolName + " tool failed because of an internal problem. "
                    + "Tell the user and suggest trying again later.", ex);
        }
    }
}
