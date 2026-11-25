package com.taskflow.ai.service;

import com.taskflow.ai.agent.PlanningAgent;
import com.taskflow.ai.agent.PriorityAgent;
import com.taskflow.ai.agent.SummaryAgent;
import com.taskflow.ai.agent.TaskAgent;
import com.taskflow.ai.agent.TaskFlowSupervisor;
import com.taskflow.ai.conversation.ConversationRef;
import com.taskflow.ai.conversation.ConversationService;
import com.taskflow.ai.dto.AgentDescriptor;
import com.taskflow.ai.dto.DailyPlan;
import com.taskflow.ai.dto.SupervisorChatResponse;
import com.taskflow.ai.exception.AiServiceException;
import com.taskflow.ai.guardrail.ConversationGuardrails;
import com.taskflow.ai.guardrail.GuardrailCodes;
import com.taskflow.ai.memory.ConversationMemory;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.guardrail.GuardrailException;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
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
 * agent/tool activity through {@link AgentRunRecorder}, apply the turn-level guardrails (3.3), commit or discard
 * the turn's chat memory and append it to the transcript (3.4), and translate provider failures into a user-safe
 * {@link AiServiceException}. No prompt or routing logic lives here; that is the supervisor's job.
 */
@Slf4j
@Service
public class MultiAgentService {

    static final String FALLBACK_MESSAGE =
            "I could not produce an answer for that request. Please try rephrasing it.";

    private final TaskFlowSupervisor supervisor;
    private final Map<String, ConversationLock> conversationLocks = new ConcurrentHashMap<>();

    /**
     * A per-conversation lock with a count of the requests using it. The count is only touched inside
     * {@code ConcurrentHashMap.compute}, which is atomic per key, so an entry is removed only when no request
     * holds or is about to take its lock; a plain "remove when unlocked" check can drop a lock another thread
     * has already fetched and let two turns of one conversation run at once.
     */
    private static final class ConversationLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }

    private final ConversationGuardrails guardrails;
    /** Phase 3.4: staged chat-memory commits, or null (in-process memory, for unit tests of the agent loop). */
    private final ConversationMemory memory;
    /** Phase 3.4: the transcript, or null (for unit tests of the agent loop). */
    private final ConversationService conversations;

    /** Without persistence: for unit tests of the agent loop. */
    public MultiAgentService(TaskFlowSupervisor supervisor, ConversationGuardrails guardrails) {
        this(supervisor, guardrails, null, null);
    }

    @Autowired
    public MultiAgentService(TaskFlowSupervisor supervisor, ConversationGuardrails guardrails,
                             ConversationMemory memory, ConversationService conversations) {
        this.supervisor = supervisor;
        this.guardrails = guardrails;
        this.memory = memory;
        this.conversations = conversations;
    }

    /**
     * @param conversation a conversation already verified to belong to the caller (see {@code ConversationService})
     */
    public SupervisorChatResponse chat(ConversationRef conversation, String message) {
        String requestId = UUID.randomUUID().toString();
        String conversationId = conversation.id().toString();
        // Locks and chat memory are keyed by the owner-qualified key, never by the bare id from the request.
        String memoryKey = conversation.memoryKey();
        ConversationLock entry = conversationLocks.compute(memoryKey, (id, existing) -> {
            ConversationLock current = existing == null ? new ConversationLock() : existing;
            current.users++;
            return current;
        });
        entry.lock.lock();
        try {
            MDC.put("requestId", requestId);
            MDC.put("conversationId", conversationId);
            return run(conversation, message, requestId);
        } finally {
            MDC.remove("requestId");
            MDC.remove("conversationId");
            entry.lock.unlock();
            conversationLocks.computeIfPresent(memoryKey, (id, current) -> --current.users == 0 ? null : current);
        }
    }

    private SupervisorChatResponse run(ConversationRef conversation, String message, String requestId) {
        String conversationId = conversation.id().toString();
        long started = System.nanoTime();
        AgentRunRecorder.Run run = AgentRunRecorder.open();
        try {
            log.info("supervisor.request chars={}", message.length());
            // Phase 3.3 input guardrails: no agent runs, no model call, nothing enters chat memory.
            var refusal = guardrails.checkInput(message);
            if (refusal.isPresent()) {
                log.info("guardrail.input code={}", refusal.get().code());
                // The refusal is part of the transcript the user sees, but never part of the model's memory.
                recordTurn(conversation, message, refusal.get().reply(), null, List.of(refusal.get().code()));
                return new SupervisorChatResponse(conversationId, refusal.get().reply(), false,
                        new SupervisorChatResponse.Execution(requestId, (System.nanoTime() - started) / 1_000_000,
                                List.of(), List.of(), 0, null),
                        null, List.of(refusal.get().code()));
            }
            // Phase 3.4: from here on, chat-memory writes are staged until the answer is known to be good.
            if (memory != null) {
                memory.begin(conversation.ownerId());
            }
            String answer;
            try {
                answer = supervisor.chat(conversation.memoryKey(), message);
            } catch (RuntimeException ex) {
                GuardrailException guardrail = guardrailCause(ex);
                if (guardrail == null) {
                    throw ex;
                }
                // An agent's answer still failed its output guardrail after the allowed reprompts
                // (OutputGuardrailsConfig.maxRetries): show the safe fallback, never the failed text.
                log.warn("guardrail.agent-gave-up error={}", guardrail.getMessage());
                AgentRunRecorder.recordGuardrail(GuardrailCodes.OUTPUT_FALLBACK);
                answer = ConversationGuardrails.fallback(run.plan(), run.dataChanged());
            }
            if (answer == null || answer.isBlank()) {
                log.warn("supervisor.empty-answer agents={}", run.agents());
                answer = FALLBACK_MESSAGE;
            }
            // Phase 3.3 final check on the combined answer (each agent already repaired its own): show it or fall back.
            answer = guardrails.checkOutput(conversation.ownerId(), message, answer, run.plan(), run.dataChanged());
            // Phase 3.4 staged commit: a rejected answer never becomes conversation memory.
            boolean rejected = run.guardrails().contains(GuardrailCodes.OUTPUT_FALLBACK)
                    || FALLBACK_MESSAGE.equals(answer);
            if (memory != null) {
                if (rejected) {
                    memory.discard();
                } else {
                    commitMemory();
                }
            }
            recordTurn(conversation, message, answer, run.plan(), run.guardrails());
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
                            tokenUsage(run.tokenUsage())),
                    run.plan(),
                    run.guardrails());
        } catch (RuntimeException ex) {
            // Tool failures never reach here (they are fed back to the model); this is a model call or the
            // planner failing: rate limit, timeout, bad key, network, unparsable plan. Log detail, return a safe message.
            AiServiceException translated = AiServiceException.translate(ex);
            log.error("supervisor.failed status={} agents={} error={}",
                    translated.status(), run.agents().stream().map(a -> a.name()).toList(), ex.toString(), ex);
            throw translated;
        } finally {
            // A turn that failed (model error, timeout) leaves memory as it was; a no-op after a commit.
            if (memory != null) {
                memory.discard();
            }
            evictScope(conversation);
            AgentRunRecorder.close();
        }
    }

    /**
     * Saves the turn's memory. A failure is logged and the turn is forgotten by the model, but the user still gets
     * the answer they already have (their tasks may have changed in this turn); the transcript records it.
     */
    private void commitMemory() {
        try {
            memory.commit();
        } catch (RuntimeException ex) {
            log.error("memory.commit-failed error={}", ex.toString(), ex);
        }
    }

    /**
     * LangChain4j keeps the supervisor's AgenticScope per memory id in process. Evicting it after every turn keeps
     * the process from growing with every conversation and makes the persisted chat memory the only thing that
     * carries over, so a discarded turn is really forgotten and a restart loses nothing.
     */
    private void evictScope(ConversationRef conversation) {
        try {
            supervisor.evictAgenticScope(conversation.memoryKey());
        } catch (RuntimeException ex) {
            log.warn("supervisor.scope-evict-failed error={}", ex.toString());
        }
    }

    /** Appends the turn to the transcript; a failure there is logged, the user still gets the answer. */
    private void recordTurn(ConversationRef conversation, String message, String answer,
                            DailyPlan plan, List<String> guardrailCodes) {
        if (conversations == null) {
            return;
        }
        try {
            conversations.recordTurn(conversation, message, answer, plan, guardrailCodes);
        } catch (RuntimeException ex) {
            log.error("conversation.transcript-failed error={}", ex.toString(), ex);
        }
    }

    /** The LangChain4j guardrail failure behind an agent failure, if any (agentic may wrap it). */
    private static GuardrailException guardrailCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof GuardrailException guardrail) {
                return guardrail;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
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
