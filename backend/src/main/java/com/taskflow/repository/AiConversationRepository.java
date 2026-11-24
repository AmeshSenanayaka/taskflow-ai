package com.taskflow.repository;

import com.taskflow.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    Optional<AiConversation> findByIdAndOwnerId(UUID id, UUID ownerId);
}
