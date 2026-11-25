package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of a conversation's transcript, as the user saw it (Phase 3.4). This is what the UI shows when a
 * conversation is resumed; what the model remembers is kept separately in {@code ai_chat_memory}.
 */
@Entity
@Table(name = "ai_chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChatMessage {

    public enum Role { USER, ASSISTANT }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Insertion order, assigned by the database (BIGSERIAL). */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = 10)
    private Role role;

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    /** JSON of the validated plan shown with the answer, if any. */
    @Column(name = "plan", updatable = false, columnDefinition = "TEXT")
    private String plan;

    /** Comma-separated guardrail codes that acted on the turn, if any. */
    @Column(name = "guardrails", updatable = false, length = 500)
    private String guardrails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AiChatMessage(UUID conversationId, Role role, String content, String plan, String guardrails,
                         Instant createdAt) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.plan = plan;
        this.guardrails = guardrails;
        this.createdAt = createdAt;
    }
}
