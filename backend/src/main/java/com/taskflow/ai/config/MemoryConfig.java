package com.taskflow.ai.config;

import com.taskflow.ai.conversation.ConversationService;
import com.taskflow.ai.memory.ChatMemoryRepository;
import com.taskflow.ai.memory.ConversationMemory;
import com.taskflow.ai.memory.ConversationRetentionJob;
import com.taskflow.ai.memory.MemoryNamespace;
import com.taskflow.ai.memory.MemoryProperties;
import com.taskflow.ai.memory.PostgresChatMemoryStore;
import com.taskflow.security.CurrentUserProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.List;

/**
 * Phase 3.4 persistent conversation memory: one PostgreSQL-backed chat memory store per agent namespace, the staged
 * commit around each turn, and the retention job.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfig {

    @Bean
    public PostgresChatMemoryStore taskAgentMemoryStore(ChatMemoryRepository repository, CurrentUserProvider currentUser,
                                                        Clock clock, MemoryProperties properties) {
        return new PostgresChatMemoryStore(MemoryNamespace.TASK_AGENT, repository, currentUser, clock,
                properties.maxStoredBytes());
    }

    @Bean
    public PostgresChatMemoryStore supervisorMemoryStore(ChatMemoryRepository repository, CurrentUserProvider currentUser,
                                                         Clock clock, MemoryProperties properties) {
        return new PostgresChatMemoryStore(MemoryNamespace.SUPERVISOR, repository, currentUser, clock,
                properties.maxStoredBytes());
    }

    @Bean
    public ConversationMemory conversationMemory(List<PostgresChatMemoryStore> stores) {
        return new ConversationMemory(stores);
    }

    @Bean
    public ConversationRetentionJob conversationRetentionJob(ConversationService conversations, Clock clock) {
        return new ConversationRetentionJob(conversations, clock);
    }
}
