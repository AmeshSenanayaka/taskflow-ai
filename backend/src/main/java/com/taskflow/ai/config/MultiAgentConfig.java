package com.taskflow.ai.config;

import com.taskflow.ai.agent.TaskFlowSupervisor;
import com.taskflow.ai.orchestration.AgentContextProvider;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import com.taskflow.ai.orchestration.TaskFlowAgentFactory;
import com.taskflow.ai.guardrail.ConversationGuardrails;
import com.taskflow.ai.guardrail.GroundedAnswerOutputGuardrail;
import com.taskflow.ai.guardrail.GuardrailProperties;
import com.taskflow.ai.memory.PostgresChatMemoryStore;
import com.taskflow.ai.tool.PlanTools;
import com.taskflow.ai.tool.TaskQueryTools;
import com.taskflow.ai.tool.TaskTools;
import com.taskflow.rules.DomainRule;
import com.taskflow.rules.plan.PlanCheck;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.DashboardService;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.TaskService;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Spring wiring of the Phase 2 multi-agent system.
 * <p>
 * The sub-agents are not exposed as beans: {@code TaskAgent} already exists as the Phase 1 {@code @AiService}
 * bean, and a second bean of the same type would make its injection ambiguous. {@link TaskFlowAgentFactory}
 * builds the supervisor together with its private sub-agent instances; the Phase 1 endpoint and the Phase 2
 * endpoint therefore run independent agents that share the same {@code TaskTools}, services and model.
 * {@code TaskQueryTools} is created here rather than as a bean so the starter does not also wire the
 * read-only tools into the Phase 1 {@code @AiService}.
 */
@Configuration
@EnableConfigurationProperties({SupervisorProperties.class, GuardrailProperties.class})
public class MultiAgentConfig {

    @Bean
    public AgentRunRecorder agentRunRecorder() {
        return new AgentRunRecorder();
    }

    @Bean
    public TaskFlowAgentFactory taskFlowAgentFactory(ChatModel chatModel,
                                                     TaskTools taskTools,
                                                     TaskService taskService,
                                                     DashboardService dashboardService,
                                                     PreferencesService preferencesService,
                                                     Clock clock,
                                                     ChatMemoryProvider chatMemoryProvider,
                                                     AgentContextProvider contextProvider,
                                                     SupervisorProperties properties,
                                                     AgentRunRecorder recorder,
                                                     CurrentUserProvider currentUser,
                                                     List<DomainRule<PlanCheck>> planRules,
                                                     GuardrailProperties guardrails,
                                                     @Qualifier("supervisorMemoryStore")
                                                     PostgresChatMemoryStore supervisorMemoryStore) {
        TaskQueryTools taskQueryTools = new TaskQueryTools(taskService, dashboardService, preferencesService, clock,
                currentUser);
        PlanTools planTools = new PlanTools(taskService, preferencesService, currentUser, planRules);
        OutputGuardrail answerGuardrail = guardrails.enabled()
                ? new GroundedAnswerOutputGuardrail(taskService, currentUser)
                : null;
        return new TaskFlowAgentFactory(chatModel, taskTools, taskQueryTools, planTools, chatMemoryProvider,
                contextProvider::render, properties, recorder, answerGuardrail, guardrails.outputRetries(),
                supervisorMemoryStore);
    }

    /** Phase 3.3: the turn-level input check and final output check around each supervisor turn. */
    @Bean
    public ConversationGuardrails conversationGuardrails(GuardrailProperties properties, TaskService taskService) {
        return new ConversationGuardrails(properties, taskService);
    }

    @Bean
    public TaskFlowSupervisor taskFlowSupervisor(TaskFlowAgentFactory factory) {
        return factory.supervisor();
    }
}
