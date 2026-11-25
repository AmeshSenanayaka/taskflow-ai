package com.taskflow.ai.guardrail;

import com.taskflow.ai.dto.DailyPlan;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.service.TaskService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The turn-level guardrails of Phase 3.3, around one supervisor turn ({@code MultiAgentService} calls them):
 * <ol>
 *   <li><b>Input</b>, once at the entry point, before any agent runs: direct prompt injection and clearly
 *       out-of-scope messages get a fixed answer, with no model call and nothing in chat memory.</li>
 *   <li><b>Final output check</b> on the combined answer of all agents. Each agent already repaired its own
 *       answer through {@link GroundedAnswerOutputGuardrail}; this last deterministic check only decides between
 *       showing the answer and the safe {@link #fallback fallback}.</li>
 * </ol>
 * Agent-level guardrails use LangChain4j's API ({@link TaskScopeInputGuardrail}, {@link GroundedAnswerOutputGuardrail});
 * tool guardrails (untrusted task data, the write limit) live in the tools.
 */
@Slf4j
public class ConversationGuardrails {

    static final String INJECTION_REPLY = "I can't follow instructions that try to change how I work. I can help you "
            + "create and update tasks, prioritise them, plan your day and summarise your progress.";
    static final String OUT_OF_SCOPE_REPLY = "I can only help with your tasks: creating and updating them, "
            + "prioritising, planning your day and summarising your progress. What would you like to do with your tasks?";

    private final boolean enabled;
    private final TaskService taskService;

    public ConversationGuardrails(GuardrailProperties properties, TaskService taskService) {
        this.enabled = properties.enabled();
        this.taskService = taskService;
    }

    /** No turn-level guardrails: for unit tests of the agent loop itself. */
    public static ConversationGuardrails disabled() {
        return new ConversationGuardrails(new GuardrailProperties(false, 100, 0), null);
    }

    /** A turn answered by an input guardrail without running any agent. */
    public record Refusal(String code, String reply) {
    }

    /** The deterministic input classification shared by this class and {@link TaskScopeInputGuardrail}. */
    public static Optional<Refusal> classifyInput(String message) {
        if (InjectionDetector.looksLikeInstructions(message)) {
            return Optional.of(new Refusal(GuardrailCodes.INPUT_PROMPT_INJECTION, INJECTION_REPLY));
        }
        if (ScopeDetector.outOfScope(message)) {
            return Optional.of(new Refusal(GuardrailCodes.INPUT_OUT_OF_SCOPE, OUT_OF_SCOPE_REPLY));
        }
        return Optional.empty();
    }

    /** The fixed reply for an input guardrail code, e.g. after {@code InputGuardrailException} on the Phase 1 path. */
    public static String replyFor(String code) {
        return GuardrailCodes.INPUT_PROMPT_INJECTION.equals(code) ? INJECTION_REPLY : OUT_OF_SCOPE_REPLY;
    }

    public Optional<Refusal> checkInput(String message) {
        Optional<Refusal> refusal = enabled ? classifyInput(message) : Optional.empty();
        if (refusal.isEmpty()) {
            // Checked once here (or deliberately not at all); agent-level input guardrails see the planner's
            // requests, not the user's message, so they stand down for the rest of this turn.
            AgentRunRecorder.markInputChecked();
        }
        return refusal;
    }

    /** @return the answer to show: the original, or the safe fallback when it still fails the checks */
    public String checkOutput(UUID ownerId, String userMessage, String answer, DailyPlan plan, boolean dataChanged) {
        if (!enabled) {
            return answer;
        }
        List<TaskResponse> tasks = AgentRunRecorder.tasksForChecks(() -> taskService.listAll(ownerId));
        // The same allowed wording as the agent-level check: the user's message and what the agents were asked,
        // so an answer an agent's guardrail accepted is not rejected here for quoting the planner's request.
        String allowed = userMessage + "\n" + String.join("\n", AgentRunRecorder.agentRequests());
        List<GuardrailViolation> violations = AnswerGuard.check(answer, tasks, allowed, plan);
        if (violations.isEmpty()) {
            return answer;
        }
        violations.forEach(violation -> AgentRunRecorder.recordGuardrail(violation.code()));
        AgentRunRecorder.recordGuardrail(GuardrailCodes.OUTPUT_FALLBACK);
        log.warn("guardrail.final-output-fallback codes={}",
                violations.stream().map(GuardrailViolation::code).distinct().toList());
        return fallback(plan, dataChanged);
    }

    /** Honest and built only from verified data: never repeats the text that failed the checks. */
    public static String fallback(DailyPlan plan, boolean dataChanged) {
        StringBuilder text = new StringBuilder("I couldn't put together a reliable answer to that, so I'm not showing it.");
        if (plan != null) {
            text.append("\n\nHere is your checked plan for ").append(plan.date()).append(":");
            plan.entries().forEach(entry -> text.append("\n").append(entry.start()).append("-").append(entry.end())
                    .append(" \"").append(entry.title()).append("\""));
        }
        text.append(dataChanged
                ? "\n\nSome of your tasks were changed; please check your task list."
                : "\n\nNo tasks were changed.");
        return text.toString();
    }
}
