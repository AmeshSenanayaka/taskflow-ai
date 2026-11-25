package com.taskflow.repository;

import com.taskflow.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, UUID> {

    /** The transcript in order; callers check the conversation's owner first. */
    List<AiChatMessage> findByConversationIdOrderBySeqAsc(UUID conversationId);
}
