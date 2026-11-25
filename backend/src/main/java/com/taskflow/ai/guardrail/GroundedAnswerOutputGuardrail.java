package com.taskflow.ai.guardrail;

import com.taskflow.ai.dto.DailyPlan;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.TaskService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LangChain4j output guardrail on every sub-agent (Phase 3.3), registered with {@code AgentBuilder.outputGuardrails}.
 * <p>
 * Runs {@link AnswerGuard} on the agent's own answer: only the user's task ids, the user's task titles and the
 * checked plan's time blocks. A failing answer is sent back to the same agent with a <b>reprompt</b> that lists the
 * problems and the facts it may use, so the repair happens where the context is. LangChain4j's
 * {@code OutputGuardrailsConfig.maxRetries} bounds the attempts; when they run out it throws
 * {@code OutputGuardrailException}, which {@code MultiAgentService} turns into the safe fallback.
 */
@Slf4j
public class GroundedAnswerOutputGuardrail implements OutputGuardrail {

    private final TaskService taskService;
    private final CurrentUserProvider currentUser;

    public GroundedAnswerOutputGuardrail(TaskService taskService, CurrentUserProvider currentUser) {
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        AiMessage message = request.responseFromLLM() == null ? null : request.responseFromLLM().aiMessage();
        String answer = message == null ? null : message.text();
        if (answer == null || answer.isBlank()) {
            return success();
        }
        UUID ownerId;
        try {
            ownerId = currentUser.userId();
        } catch (RuntimeException ex) {
            // No signed-in user: the tools refuse every call in that case, so there is nothing to ground against.
            return success();
        }
        List<TaskResponse> tasks = AgentRunRecorder.tasksForChecks(() -> taskService.listAll(ownerId));
        DailyPlan plan = AgentRunRecorder.currentPlan();
        List<GuardrailViolation> violations = AnswerGuard.check(answer, tasks, requestText(request), plan);
        if (violations.isEmpty()) {
            if (AgentRunRecorder.takeRepairPending()) {
                AgentRunRecorder.recordGuardrail(GuardrailCodes.OUTPUT_REWRITTEN);
            }
            return success();
        }
        violations.forEach(violation -> AgentRunRecorder.recordGuardrail(violation.code()));
        AgentRunRecorder.markRepairPending();
        log.warn("guardrail.agent-output-failed codes={}",
                violations.stream().map(GuardrailViolation::code).distinct().toList());
        String problems = violations.stream().map(GuardrailViolation::message).collect(Collectors.joining(" "));
        return reprompt(problems, repairInstructions(violations, tasks, plan));
    }

    /** Starts every reprompt, so the guardrail can recognise its own message in the next request. */
    static final String REPROMPT_MARKER = "Your answer failed TaskFlow's automatic checks:";

    static String repairInstructions(List<GuardrailViolation> violations, List<TaskResponse> tasks, DailyPlan plan) {
        String titles = tasks.stream().limit(200).map(task -> "- \"" + task.title() + "\" (" + task.status() + ")")
                .collect(Collectors.joining("\n"));
        StringBuilder text = new StringBuilder(REPROMPT_MARKER + "\n");
        violations.forEach(violation -> text.append("- ").append(violation.message()).append("\n"));
        text.append("Rewrite the answer. Mention only the user's tasks listed below, quoting their exact titles in ")
                .append("double quotes, never write task ids, and do not add new information.\n")
                .append("The user's tasks:\n").append(titles.isEmpty() ? "none" : titles);
        if (plan != null) {
            text.append("\nUse exactly these time blocks of the checked plan:\n").append(plan.entries().stream()
                    .map(entry -> entry.start() + "-" + entry.end() + " \"" + entry.title() + "\"")
                    .collect(Collectors.joining("\n")));
        }
        return text.toString();
    }

    /**
     * What the agent was asked; quoting it back is not an invented task. The guardrail's own reprompts are left out:
     * they quote the invented title in their list of problems, and must not turn it into an allowed phrase.
     */
    private static String requestText(OutputGuardrailRequest request) {
        if (request.chatRequest() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : request.chatRequest().messages()) {
            if (message instanceof UserMessage user && user.hasSingleText()
                    && !user.singleText().contains(REPROMPT_MARKER)) {
                text.append(user.singleText()).append("\n");
            }
        }
        return text.toString();
    }
}
