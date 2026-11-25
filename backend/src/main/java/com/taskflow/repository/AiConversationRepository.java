package com.taskflow.repository;

import com.taskflow.entity.AiConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    Optional<AiConversation> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** The owner's conversations, most recent activity first. */
    List<AiConversation> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId, Pageable page);

    /** Ids of the owner's conversations beyond the newest {@code keep}, for the per-user limit. */
    @Query(value = """
            SELECT id FROM ai_conversations WHERE owner_id = :ownerId
            ORDER BY updated_at DESC, created_at DESC OFFSET :keep
            """, nativeQuery = true)
    List<UUID> findIdsBeyondNewest(@Param("ownerId") UUID ownerId, @Param("keep") int keep);

    /** Deletes inactive conversations; memory and transcript go with them (ON DELETE CASCADE). */
    @Modifying
    @Query(value = "DELETE FROM ai_conversations WHERE updated_at < :cutoff", nativeQuery = true)
    int deleteInactiveSince(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = "DELETE FROM ai_conversations WHERE id IN (:ids)", nativeQuery = true)
    int deleteAllByIdIn(@Param("ids") List<UUID> ids);
}
