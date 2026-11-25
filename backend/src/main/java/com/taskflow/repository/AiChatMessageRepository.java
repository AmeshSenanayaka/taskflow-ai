package com.taskflow.repository;

import com.taskflow.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, UUID> {

    /** The newest 200 transcript lines, newest first; callers check the conversation's owner first. */
    List<AiChatMessage> findTop200ByConversationIdOrderBySeqDesc(UUID conversationId);
}
