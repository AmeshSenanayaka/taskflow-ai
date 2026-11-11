package com.taskflow.ai.orchestration;

import com.taskflow.ai.dto.AgentCallInfo;
import com.taskflow.ai.dto.ToolCallInfo;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A LangChain4j {@link AgentListener} that records, per HTTP request, which agents the supervisor invoked,
 * how long each took, which tools they called and how many tokens were used. The record lives in a
 * {@link ThreadLocal} opened by {@code MultiAgentService} for the duration of one request; the supervisor
 * runs sub-agents synchronously on the calling thread, so the thread-local is sufficient.
 * <p>
 * Everything recorded here is also logged at INFO so a developer can follow a request in the console:
 * {@code agent.invoked name=PlanningAgent durationMs=1830 tools=[getPlanningContext, findTasks]}.
 */
@Slf4j
public class AgentRunRecorder implements AgentListener {

    /** Tool names whose successful execution means task data changed and the UI should refresh. */
    private static final Set<String> MUTATING_TOOLS = Set.of("createTask", "updateTask", "updateTaskStatus");

    private static final ThreadLocal<Run> CURRENT = new ThreadLocal<>();

    /** Mutable per-request record. */
    public static final class Run {
        private final List<AgentCallInfo> agents = new ArrayList<>();
        private final List<ToolCallInfo> toolCalls = new ArrayList<>();
        private final Map<String, Long> startedAt = new HashMap<>();
        private TokenUsage tokenUsage;
        private int llmCalls;
        private String lastAgentOutput;
        private boolean dataChanged;

        public List<AgentCallInfo> agents() {
            return List.copyOf(agents);
        }

        public List<ToolCallInfo> toolCalls() {
            return List.copyOf(toolCalls);
        }

        public TokenUsage tokenUsage() {
            return tokenUsage;
        }

        public int llmCalls() {
            return llmCalls;
        }

        public boolean dataChanged() {
            return dataChanged;
        }
    }

    /** Starts recording for the current thread. Always pair with {@link #close()} in a finally block. */
    public static Run open() {
        Run run = new Run();
        CURRENT.set(run);
        return run;
    }

    public static void close() {
        CURRENT.remove();
    }

    /** Called by {@link TokenAccountingChatModel} after every LLM call of the current request. */
    static void recordLlmCall(TokenUsage usage) {
        Run run = CURRENT.get();
        if (run != null) {
            run.llmCalls++;
            if (usage != null) {
                run.tokenUsage = run.tokenUsage == null ? usage : run.tokenUsage.add(usage);
            }
        }
    }

    /** The text answer of the last sub-agent that completed in the current request, or null. */
    static String lastAgentOutput() {
        Run run = CURRENT.get();
        return run == null ? null : run.lastAgentOutput;
    }

    @Override
    public boolean inheritedBySubagents() {
        // Sub-agents (TaskAgent, PriorityAgent, ...) report their tool executions through the same listener.
        return true;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        Run run = CURRENT.get();
        if (run != null) {
            run.startedAt.put(request.agentId(), System.nanoTime());
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        Run run = CURRENT.get();
        if (run == null) {
            return;
        }
        Long started = run.startedAt.remove(response.agentId());
        long durationMs = started == null ? 0 : (System.nanoTime() - started) / 1_000_000;
        boolean subAgent = response.agent().parent() != null;
        if (subAgent) {
            // The supervisor's own completion is not a "hop" the user cares about; only sub-agents are listed.
            run.agents.add(new AgentCallInfo(response.agentName(), durationMs, true));
            if (response.output() instanceof String text) {
                run.lastAgentOutput = text;
            }
        }
        log.info("agent.invoked name={} durationMs={} subAgent={}", response.agentName(), durationMs, subAgent);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        Run run = CURRENT.get();
        if (run != null) {
            Long started = run.startedAt.remove(error.agentId());
            long durationMs = started == null ? 0 : (System.nanoTime() - started) / 1_000_000;
            if (error.agent().parent() != null) {
                run.agents.add(new AgentCallInfo(error.agentName(), durationMs, false));
            }
        }
        log.warn("agent.failed name={} error={}", error.agentName(), String.valueOf(error.error()));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution event) {
        ToolExecution execution = event.toolExecution();
        String name = execution.request().name();
        boolean success = !execution.hasFailed();
        long durationMs = execution.duration() == null ? 0 : execution.duration().toMillis();
        Run run = CURRENT.get();
        if (run != null) {
            run.toolCalls.add(new ToolCallInfo(name, durationMs, success));
            if (success && MUTATING_TOOLS.contains(name)) {
                run.dataChanged = true;
            }
        }
        log.info("tool.executed agent={} tool={} durationMs={} success={}",
                event.agentInstance().name(), name, durationMs, success);
    }
}
