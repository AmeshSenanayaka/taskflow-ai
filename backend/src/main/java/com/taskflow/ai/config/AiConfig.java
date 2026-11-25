package com.taskflow.ai.config;

import com.taskflow.ai.memory.MemoryProperties;
import com.taskflow.ai.memory.PostgresChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the LangChain4j AI Service.
 * <p>
 * {@code TaskAgent.chat(@MemoryId conversationId, ...)} asks LangChain4j to keep a separate chat memory per
 * conversation, which requires a {@link ChatMemoryProvider} bean: the Spring starter picks it up automatically
 * when it builds the {@code @AiService} proxy. Each conversation gets a sliding window of the most recent
 * messages (the system message is always kept), so the agent can resolve follow-ups such as "change its
 * priority to LOW" without the whole transcript growing without bound.
 * <p>
 * Since Phase 3.4 the window is stored in PostgreSQL ({@link PostgresChatMemoryStore}, namespace
 * {@code task-agent}), so it survives restarts; the agent itself did not change. The same provider serves TaskAgent
 * as the Phase 1 agent and as the supervisor's sub-agent.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatMemoryProvider chatMemoryProvider(@Qualifier("taskAgentMemoryStore") PostgresChatMemoryStore taskAgentMemoryStore,
                                                 MemoryProperties properties) {
        return conversationId -> MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(properties.maxMessages())
                .chatMemoryStore(taskAgentMemoryStore)
                .build();
    }
}
