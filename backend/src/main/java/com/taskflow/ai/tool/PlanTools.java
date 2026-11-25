package com.taskflow.ai.tool;

import com.taskflow.ai.dto.DailyPlan;
import com.taskflow.ai.exception.ToolExecutionException;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.exception.TaskNotFoundException;
import com.taskflow.rules.DomainRule;
import com.taskflow.rules.RuleEngine;
import com.taskflow.rules.RuleViolation;
import com.taskflow.rules.plan.PlanCheck;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.TaskService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The PlanningAgent's way to hand in a plan (Phase 3.2). Instead of trusting free text, the agent submits time
 * blocks; deterministic plan rules check them (fits the available minutes, only the user's open tasks, no
 * overlaps, well formed). A rejected plan comes back with the reasons so the agent can fix it; an accepted plan is
 * recorded for the response, so the client receives exactly what was validated.
 * <p>
 * Read-only for task data: submitting a plan changes no task. Not a Spring bean for the same reason as
 * {@link TaskQueryTools}: only the PlanningAgent gets it.
 */
@Slf4j
public class PlanTools {

    /** After this many rejected submissions in one request the agent must give up and tell the user. */
    static final int MAX_ATTEMPTS = 3;

    private final TaskService taskService;
    private final PreferencesService preferencesService;
    private final CurrentUserProvider currentUser;
    private final List<DomainRule<PlanCheck>> rules;

    public PlanTools(TaskService taskService, PreferencesService preferencesService, CurrentUserProvider currentUser,
                     List<DomainRule<PlanCheck>> rules) {
        this.taskService = taskService;
        this.preferencesService = preferencesService;
        this.currentUser = currentUser;
        this.rules = List.copyOf(rules);
    }

    /** One time block as the model sends it; every field is checked, none is trusted. */
    public record PlanBlock(String taskId, String start, Integer minutes) {
    }

    /** @param plan the accepted plan, or null when it was rejected */
    public record PlanSubmission(boolean accepted, List<String> problems, DailyPlan plan, String instruction) {
    }

    @Tool("""
            Submit the day plan for validation before you present it. Each block is one of the user's open tasks:
            taskId (from findTasks), start as HH:mm (24-hour) and minutes. Use a task's estimatedMinutes when it
            has one. The plan must fit in the daily available minutes, use each task at most once, have no
            overlapping blocks and only include open tasks. If it is rejected, fix every listed problem and submit
            again. Present to the user exactly the accepted plan.
            """)
    public PlanSubmission submitPlan(
            @P("The day the plan is for, YYYY-MM-DD (usually today)") String date,
            @P("The time blocks, in order: each with taskId, start (HH:mm) and minutes") List<PlanBlock> blocks
    ) {
        UUID userId = currentUser.userId();
        LocalDate today = preferencesService.today(userId);
        PreferencesResponse preferences = preferencesService.get(userId);
        int available = preferences.dailyAvailableMinutes() == null ? 0 : preferences.dailyAvailableMinutes();

        List<PlanCheck.Block> parsed = new ArrayList<>();
        Map<UUID, TaskResponse> ownTasks = new HashMap<>();
        for (PlanBlock block : blocks == null ? List.<PlanBlock>of() : blocks) {
            UUID taskId = parseUuid(block.taskId());
            parsed.add(new PlanCheck.Block(taskId, parseTime(block.start()), block.minutes(),
                    "(" + block.taskId() + " at " + block.start() + " for " + block.minutes() + " min)"));
            if (taskId != null && !ownTasks.containsKey(taskId)) {
                try {
                    ownTasks.put(taskId, taskService.getById(userId, taskId));
                } catch (TaskNotFoundException ex) {
                    // Unknown or another user's task: absent from ownTasks, reported by the rules.
                }
            }
        }
        LocalDate planDate = date == null || date.isBlank() ? today : parseDate(date);
        PlanCheck check = new PlanCheck(planDate, today, available, parsed, ownTasks);
        List<RuleViolation> violations = RuleEngine.evaluate(rules, check);
        int attempt = AgentRunRecorder.nextPlanAttempt();

        if (!violations.isEmpty()) {
            List<String> problems = violations.stream().map(RuleViolation::message).toList();
            log.info("plan.rejected attempt={} rules={}", attempt, violations.stream().map(RuleViolation::rule).toList());
            if (attempt >= MAX_ATTEMPTS) {
                throw new ToolExecutionException("The plan was rejected " + attempt + " times. Do not submit again: "
                        + "tell the user you could not build a plan that fits, and why: " + String.join(" ", problems));
            }
            return new PlanSubmission(false, problems, null, "Fix every problem and call submitPlan again.");
        }

        DailyPlan plan = toPlan(check);
        AgentRunRecorder.recordPlan(plan);
        log.info("plan.accepted attempt={} blocks={} minutes={}/{}", attempt, plan.entries().size(),
                plan.totalMinutes(), plan.availableMinutes());
        return new PlanSubmission(true, List.of(), plan,
                "Plan accepted. Present exactly these blocks to the user, then list deferred tasks and assumptions.");
    }

    private static DailyPlan toPlan(PlanCheck check) {
        List<DailyPlan.Entry> entries = check.blocks().stream()
                .sorted(Comparator.comparing(PlanCheck.Block::start))
                .map(block -> {
                    TaskResponse task = check.ownTasks().get(block.taskId());
                    return new DailyPlan.Entry(task.id(), task.title(), task.priority(), task.dueDate(),
                            block.start().toString(), block.end().toString(), block.minutes());
                })
                .toList();
        int total = entries.stream().mapToInt(DailyPlan.Entry::minutes).sum();
        return new DailyPlan(check.date(), check.availableMinutes(), total, entries);
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return value == null ? null : LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
