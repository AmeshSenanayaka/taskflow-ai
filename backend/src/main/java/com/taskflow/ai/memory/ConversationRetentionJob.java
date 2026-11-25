package com.taskflow.ai.memory;

import com.taskflow.ai.conversation.ConversationService;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;

/** Applies {@code taskflow.ai.memory.retention} on a schedule (Phase 3.4). */
public class ConversationRetentionJob {

    private final ConversationService conversations;
    private final Clock clock;

    public ConversationRetentionJob(ConversationService conversations, Clock clock) {
        this.conversations = conversations;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${taskflow.ai.memory.cleanup-interval:PT1H}")
    public void deleteInactiveConversations() {
        conversations.deleteInactive(Instant.now(clock));
    }
}
