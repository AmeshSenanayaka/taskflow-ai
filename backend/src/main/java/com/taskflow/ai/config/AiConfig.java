package com.taskflow.ai.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
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
 * Phase 1 keeps memory in process (lost on restart). A persistent {@code ChatMemoryStore} can be plugged in
 * later without touching the agent.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatMemoryProvider chatMemoryProvider(
            @Value("${taskflow.ai.memory.max-messages:30}") int maxMessages) {
        return conversationId -> MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(maxMessages)
                .build();
    }
}
