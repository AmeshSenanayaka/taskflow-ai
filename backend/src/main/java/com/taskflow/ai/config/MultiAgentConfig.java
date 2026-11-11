package com.taskflow.ai.config;

import com.taskflow.ai.agent.TaskFlowSupervisor;
import com.taskflow.ai.orchestration.AgentContextProvider;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import com.taskflow.ai.orchestration.TaskFlowAgentFactory;
import com.taskflow.ai.tool.TaskQueryTools;
import com.taskflow.ai.tool.TaskTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring of the Phase 2 multi-agent system.
 * <p>
 * The sub-agents are not exposed as beans: {@code TaskAgent} already exists as the Phase 1 {@code @AiService}
 * bean, and a second bean of the same type would make its injection ambiguous. {@link TaskFlowAgentFactory}
 * builds the supervisor together with its private sub-agent instances; the Phase 1 endpoint and the Phase 2
 * endpoint therefore run independent agents that share the same tools, services and model.
 */
@Configuration
@EnableConfigurationProperties(SupervisorProperties.class)
public class MultiAgentConfig {

    @Bean
    public AgentRunRecorder agentRunRecorder() {
        return new AgentRunRecorder();
    }

    @Bean
    public TaskFlowAgentFactory taskFlowAgentFactory(ChatModel chatModel,
                                                     TaskTools taskTools,
                                                     TaskQueryTools taskQueryTools,
                                                     ChatMemoryProvider chatMemoryProvider,
                                                     AgentContextProvider contextProvider,
                                                     SupervisorProperties properties,
                                                     AgentRunRecorder recorder) {
        return new TaskFlowAgentFactory(chatModel, taskTools, taskQueryTools, chatMemoryProvider,
                contextProvider::render, properties, recorder);
    }

    @Bean
    public TaskFlowSupervisor taskFlowSupervisor(TaskFlowAgentFactory factory) {
        return factory.supervisor();
    }
}
