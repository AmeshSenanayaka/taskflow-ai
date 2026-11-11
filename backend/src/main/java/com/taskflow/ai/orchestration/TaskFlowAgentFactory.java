package com.taskflow.ai.orchestration;

import com.taskflow.ai.agent.PlanningAgent;
import com.taskflow.ai.agent.PriorityAgent;
import com.taskflow.ai.agent.SummaryAgent;
import com.taskflow.ai.agent.TaskAgent;
import com.taskflow.ai.agent.TaskFlowSupervisor;
import com.taskflow.ai.config.SupervisorProperties;
import com.taskflow.ai.exception.ToolExecutionException;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgentService;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * Builds the Phase 2 agentic system with {@link AgenticServices}: four specialised agents and the
 * {@link TaskFlowSupervisor} that routes between them.
 * <p>
 * This is plain Java on purpose (no Spring annotations): {@code MultiAgentConfig} calls it with the
 * production beans, and the tests call it with a scripted {@link ChatModel}. Every agent shares the
 * same model, the same {@link AgentRunRecorder} listener and the same error handling, so behaviour
 * differs only by prompt, tools and memory:
 * <ul>
 *   <li>{@link TaskAgent}: full {@code TaskTools} (read + write) and per-conversation memory.</li>
 *   <li>{@link PriorityAgent}, {@link PlanningAgent}, {@link SummaryAgent}: read-only
 *       {@code TaskQueryTools}, no memory (each analysis starts from the live data).</li>
 * </ul>
 */
@Slf4j
public class TaskFlowAgentFactory {

    private static final String SUPERVISOR_CONTEXT_RESOURCE = "/prompts/supervisor-context.txt";
    static final String SUPERVISOR_OUTPUT_KEY = "supervisorAnswer";

    private final ChatModel chatModel;
    private final Object taskTools;
    private final Object taskQueryTools;
    private final ChatMemoryProvider taskAgentMemory;
    private final Supplier<String> contextSupplier;
    private final SupervisorProperties properties;
    private final AgentRunRecorder recorder;

    /**
     * @param chatModel       the model every agent (and the supervisor's planner) uses
     * @param taskTools       the read/write tool object for the TaskAgent ({@code TaskTools})
     * @param taskQueryTools  the read-only tool object for the analytical agents ({@code TaskQueryTools})
     * @param taskAgentMemory memory provider for the TaskAgent (one window per conversation id)
     * @param contextSupplier renders the current-date context appended to every system message
     * @param properties      limits and strategies
     * @param recorder        listener that records agent and tool invocations per request
     */
    public TaskFlowAgentFactory(ChatModel chatModel,
                                Object taskTools,
                                Object taskQueryTools,
                                ChatMemoryProvider taskAgentMemory,
                                Supplier<String> contextSupplier,
                                SupervisorProperties properties,
                                AgentRunRecorder recorder) {
        // Every LLM call of every agent goes through the accounting wrapper, so the per-request
        // record gets exact call and token counts whatever the provider.
        this.chatModel = new TokenAccountingChatModel(chatModel);
        this.taskTools = taskTools;
        this.taskQueryTools = taskQueryTools;
        this.taskAgentMemory = taskAgentMemory;
        this.contextSupplier = contextSupplier;
        this.properties = properties;
        this.recorder = recorder;
    }

    public TaskAgent taskAgent() {
        return configure(AgenticServices.agentBuilder(TaskAgent.class), taskTools)
                .chatMemoryProvider(taskAgentMemory)
                .build();
    }

    public PriorityAgent priorityAgent() {
        return configure(AgenticServices.agentBuilder(PriorityAgent.class), taskQueryTools).build();
    }

    public PlanningAgent planningAgent() {
        return configure(AgenticServices.agentBuilder(PlanningAgent.class), taskQueryTools).build();
    }

    public SummaryAgent summaryAgent() {
        return configure(AgenticServices.agentBuilder(SummaryAgent.class), taskQueryTools).build();
    }

    /** Builds the supervisor over freshly built sub-agents. */
    public TaskFlowSupervisor supervisor() {
        return supervisor(List.of(taskAgent(), priorityAgent(), planningAgent(), summaryAgent()));
    }

    public TaskFlowSupervisor supervisor(List<Object> subAgents) {
        SupervisorAgentService<TaskFlowSupervisor> builder = AgenticServices.supervisorBuilder(TaskFlowSupervisor.class)
                .chatModel(chatModel)
                .chatMemoryProvider(supervisorMemory())
                .subAgents(subAgents)
                .supervisorContext(readResource(SUPERVISOR_CONTEXT_RESOURCE))
                .contextGenerationStrategy(properties.contextStrategy())
                .maxAgentsInvocations(properties.maxAgentInvocations())
                .outputKey(SUPERVISOR_OUTPUT_KEY)
                .listener(recorder);
        if (properties.responseStrategy() == SupervisorResponseStrategy.LAST) {
            // LangChain4j's own LAST strategy returns null when the planner answers without calling any agent
            // (greetings, "what can you do") and drops every answer but the last when it called several
            // ("summarise my week and then plan tomorrow"). We therefore let the planner always write its recap
            // (SUMMARY) and prefer the sub-agents' verbatim answers whenever there are any: a plan or a summary
            // must reach the user unchanged, not re-told by the planner, and none of them may be lost.
            builder.responseStrategy(SupervisorResponseStrategy.SUMMARY)
                    .output(TaskFlowAgentFactory::agentAnswersOrRecap);
        } else {
            builder.responseStrategy(properties.responseStrategy());
        }
        return builder.build();
    }

    /** Every sub-agent's answer in the order they ran (one agent: its answer verbatim), else the planner's recap. */
    static Object agentAnswersOrRecap(AgenticScope scope) {
        List<String> answers = AgentRunRecorder.agentAnswers();
        if (!answers.isEmpty()) {
            return String.join("\n\n", answers);
        }
        return scope.readState(SUPERVISOR_OUTPUT_KEY);
    }

    private <T> AgentBuilder<T, ?> configure(AgentBuilder<T, ?> builder, Object tools) {
        return builder
                .chatModel(chatModel)
                .tools(tools)
                .maxSequentialToolsInvocations(properties.maxToolRoundTrips())
                .toolExecutionErrorHandler(toolErrorHandler())
                .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
                .systemMessageTransformer(systemMessage -> systemMessage + contextSupplier.get())
                .listener(recorder);
    }

    /**
     * Tool failures become a message for the model, not an exception for the user. {@link ToolExecutionException}
     * messages are written for the model by the tools themselves; anything else is replaced by a generic text so
     * no internal detail leaks into the conversation.
     */
    static ToolExecutionErrorHandler toolErrorHandler() {
        return (error, context) -> {
            String tool = context.toolExecutionRequest().name();
            if (error instanceof ToolExecutionException) {
                return ToolErrorHandlerResult.text("Error: " + error.getMessage());
            }
            log.error("Tool {} failed unexpectedly", tool, error);
            return ToolErrorHandlerResult.text("Error: the " + tool
                    + " tool failed because of an internal problem. Tell the user and suggest trying again later.");
        };
    }

    private ChatMemoryProvider supervisorMemory() {
        return conversationId -> MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(properties.memoryMaxMessages())
                .build();
    }

    static String readResource(String path) {
        try (InputStream in = TaskFlowAgentFactory.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read " + path, ex);
        }
    }
}
