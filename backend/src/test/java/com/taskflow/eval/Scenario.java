package com.taskflow.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One evaluation scenario, read from {@code src/test/resources/eval/scenarios/*.json}.
 * <p>
 * A scenario is self-contained: who is signed in, what "now" is, which tasks exist (for any user), what the user says
 * (one or more turns) and what must be true afterwards. {@link #script()} is optional: it is the scripted model's
 * answers for the regression tier; scenarios without one run only against the real model.
 *
 * @param notApplicable set when the scenario describes behaviour a later milestone adds; it is listed in every report
 *                      but never executed and never counted
 */
public record Scenario(
        String id,
        String title,
        Category category,
        String user,
        Context context,
        NotApplicable notApplicable,
        List<FixtureTask> fixture,
        List<String> turns,
        List<ScriptStep> script,
        Expectations expect
) {

    public enum Category { GOLDEN, TRAJECTORY, AUTHORIZATION, MEMORY, GUARDRAIL, GOVERNANCE, FLAGSHIP }

    public boolean executable() {
        return notApplicable == null;
    }

    public boolean scripted() {
        return script != null && !script.isEmpty();
    }

    public String userOrDefault() {
        return user == null ? "alice" : user;
    }

    public Context contextOrDefault() {
        return context == null ? Context.DEFAULT : context.withDefaults();
    }

    public List<FixtureTask> fixtureOrEmpty() {
        return fixture == null ? List.of() : fixture;
    }

    public List<ScriptStep> scriptOrEmpty() {
        return script == null ? List.of() : script;
    }

    public Expectations expectOrEmpty() {
        return expect == null ? Expectations.NONE : expect;
    }

    /** "Now", the user's timezone and daily minutes. The clock is fixed so "today" and "overdue" are repeatable. */
    public record Context(Instant now, String timezone, Integer dailyAvailableMinutes) {
        /** Monday 2026-11-09, 09:00 in Asia/Colombo. */
        public static final Context DEFAULT = new Context(Instant.parse("2026-11-09T03:30:00Z"), "Asia/Colombo", 480);

        Context withDefaults() {
            return new Context(
                    now == null ? DEFAULT.now : now,
                    timezone == null ? DEFAULT.timezone : timezone,
                    dailyAvailableMinutes == null ? DEFAULT.dailyAvailableMinutes : dailyAvailableMinutes);
        }
    }

    /** @param until the milestone that makes the scenario executable, e.g. "3.4" */
    public record NotApplicable(String until, String reason) {
    }

    /**
     * A task that exists before the first turn. {@code key} lets turns, scripts and expectations refer to its id as
     * {@code ${task:key}}. {@code owner} defaults to the scenario's user.
     */
    public record FixtureTask(String key, String owner, String title, String description,
                              TaskPriority priority, TaskStatus status, LocalDate dueDate) {
    }

    /**
     * One scripted model answer. Exactly one of {@code invokeAgent} (planner picks an agent, with {@code request}),
     * {@code toolCall} (agent calls a tool, with {@code arguments}), {@code text} (agent answers) or {@code done}
     * (planner finishes) is set. Arguments may use {@code ${task:key}} and {@code ${title:fragment}}; the latter is
     * resolved when the step runs, to the id of the user's task whose title contains the fragment.
     */
    public record ScriptStep(String invokeAgent, String request, String toolCall, JsonNode arguments,
                             String text, String done) {
    }

    /**
     * What must hold after the last turn. Every field is optional.
     *
     * @param agents              agents that must (not) run, over all turns
     * @param tools               tools that must (not) be called successfully, over all turns
     * @param dataChanged         whether any turn reported a data change
     * @param maxLlmCalls         upper bound on LLM calls over all turns
     * @param responseContains    case-insensitive fragments the final answer must contain
     * @param responseNotContains case-insensitive fragments no answer may contain
     * @param tasks               expected state of individual tasks
     * @param taskCount           expected number of tasks per owner ("alice", "bob")
     * @param unchanged           fixture keys whose task must be exactly as before
     */
    public record Expectations(Names agents, Names tools, Boolean dataChanged, Integer maxLlmCalls,
                               List<String> responseContains, List<String> responseNotContains,
                               List<TaskExpectation> tasks, Map<String, Integer> taskCount, List<String> unchanged) {
        static final Expectations NONE = new Expectations(null, null, null, null, null, null, null, null, null);
    }

    /** @param none true when no agent may run at all (the planner answers directly) */
    public record Names(List<String> required, List<String> forbidden, Boolean none) {
    }

    /**
     * Identifies a task by fixture {@code key}, exact {@code title} or {@code titleContains} (case-insensitive, within
     * {@code owner}, default the scenario's user) and states what must be true about it.
     */
    public record TaskExpectation(String key, String title, String titleContains, String owner, Boolean exists,
                                  TaskStatus status, TaskPriority priority, LocalDate dueDate) {
    }
}
