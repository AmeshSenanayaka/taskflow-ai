package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An AI assistant conversation and its owner. The server issues the id, so a client can only continue a
 * conversation it was given; chat memory itself is still in process (persistent memory is Phase 3.4).
 */
@Entity
@Table(name = "ai_conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiConversation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AiConversation(UUID ownerId, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.createdAt = now;
    }
}
