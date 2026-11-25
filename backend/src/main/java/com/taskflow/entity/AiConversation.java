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
 * conversation it was given. Since Phase 3.4 its chat memory ({@code ai_chat_memory}) and transcript
 * ({@code ai_chat_messages}) are stored too, and are deleted with it.
 */
@Entity
@Table(name = "ai_conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiConversation {

    /** Longest title kept: the start of the first message. */
    public static final int TITLE_LENGTH = 80;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    /** "agent" (Phase 1 TaskAgent) or "supervisor" (Phase 2 multi-agent): the two keep separate memory. */
    @Column(name = "mode", nullable = false, updatable = false, length = 20)
    private String mode;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Last activity; the retention job deletes conversations inactive for longer than the retention period. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AiConversation(UUID ownerId, String mode, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.mode = mode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Records activity; the first user message becomes the title. */
    public void touch(String firstMessage, Instant now) {
        this.updatedAt = now;
        if (title == null && firstMessage != null && !firstMessage.isBlank()) {
            String text = firstMessage.strip().replaceAll("\\s+", " ");
            this.title = text.length() <= TITLE_LENGTH ? text : text.substring(0, TITLE_LENGTH - 1) + "…";
        }
    }
}
