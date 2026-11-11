package com.taskflow.ai.service;

import com.taskflow.ai.agent.PlanningAgent;
import com.taskflow.ai.agent.PriorityAgent;
import com.taskflow.ai.agent.SummaryAgent;
import com.taskflow.ai.agent.TaskAgent;
import com.taskflow.ai.agent.TaskFlowSupervisor;
import com.taskflow.ai.dto.AgentDescriptor;
import com.taskflow.ai.dto.SupervisorChatResponse;
import com.taskflow.ai.exception.AiServiceException;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Application service of the Phase 2 multi-agent system: runs one user message through the
 * {@link TaskFlowSupervisor} and turns the outcome into the API response.
 * <p>
 * Responsibilities, in order: serialise messages of the same conversation (two concurrent turns would
 * interleave the shared chat memories), tag the logs with a request id and the conversation id, record the
 * agent/tool activity through {@link AgentRunRecorder}, and translate provider failures into a user-safe
 * {@link AiServiceException}. No prompt or routing logic lives here; that is the supervisor's job.
 */
@Slf4j
@Service
public class MultiAgentService {

    static final String FALLBACK_MESSAGE =
            "I could not produce an answer for that request. Please try rephrasing it.";

    private final TaskFlowSupervisor supervisor;
    private final Map<String, ReentrantLock> conversationLocks = new ConcurrentHashMap<>();

    public MultiAgentService(TaskFlowSupervisor supervisor) {
        this.supervisor = supervisor;
    }

    public SupervisorChatResponse chat(String conversationId, String message) {
        String requestId = UUID.randomUUID().toString();
        ReentrantLock lock = conversationLocks.computeIfAbsent(conversationId, id -> new ReentrantLock());
        lock.lock();
        try {
            MDC.put("requestId", requestId);
            MDC.put("conversationId", conversationId);
            return run(conversationId, message, requestId);
        } finally {
            MDC.remove("requestId");
            MDC.remove("conversationId");
            lock.unlock();
            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                conversationLocks.remove(conversationId, lock);
            }
        }
    }

    private SupervisorChatResponse run(String conversationId, String message, String requestId) {
        long started = System.nanoTime();
        AgentRunRecorder.Run run = AgentRunRecorder.open();
        try {
            log.info("supervisor.request chars={}", message.length());
            String answer = supervisor.chat(conversationId, message);
            if (answer == null || answer.isBlank()) {
                log.warn("supervisor.empty-answer agents={}", run.agents());
                answer = FALLBACK_MESSAGE;
            }
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            log.info("supervisor.completed durationMs={} llmCalls={} agents={} tools={} dataChanged={}",
                    durationMs,
                    run.llmCalls(),
                    run.agents().stream().map(a -> a.name()).toList(),
                    run.toolCalls().stream().map(t -> t.name()).toList(),
                    run.dataChanged());
            return new SupervisorChatResponse(
                    conversationId,
                    answer,
                    run.dataChanged(),
                    new SupervisorChatResponse.Execution(
                            requestId, durationMs, run.agents(), run.toolCalls(), run.llmCalls(),
                            tokenUsage(run.tokenUsage())));
        } catch (RuntimeException ex) {
            // Tool failures never reach here (they are fed back to the model); this is a model call or the
            // planner failing: rate limit, timeout, bad key, network, unparsable plan. Log detail, return a safe message.
            AiServiceException translated = AiServiceException.translate(ex);
            log.error("supervisor.failed status={} agents={} error={}",
                    translated.status(), run.agents().stream().map(a -> a.name()).toList(), ex.toString(), ex);
            throw translated;
        } finally {
            AgentRunRecorder.close();
        }
    }

    /** The agent cards exactly as the supervisor's planner sees them, for the UI and the API docs. */
    public List<AgentDescriptor> agents() {
        return List.of(
                describe(TaskAgent.class, true),
                describe(PriorityAgent.class, false),
                describe(PlanningAgent.class, false),
                describe(SummaryAgent.class, false));
    }

    private static AgentDescriptor describe(Class<?> agentInterface, boolean canModifyTasks) {
        Agent agent = Arrays.stream(agentInterface.getMethods())
                .map(method -> method.getAnnotation(Agent.class))
                .filter(annotation -> annotation != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(agentInterface + " has no @Agent method"));
        return new AgentDescriptor(agent.name(), agent.description().strip().replaceAll("\\s+", " "), canModifyTasks);
    }

    private static SupervisorChatResponse.TokenUsageInfo tokenUsage(TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        return new SupervisorChatResponse.TokenUsageInfo(
                usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
    }
}
