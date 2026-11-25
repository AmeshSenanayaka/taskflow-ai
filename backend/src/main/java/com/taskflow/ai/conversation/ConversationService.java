package com.taskflow.ai.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.ai.dto.ConversationMessage;
import com.taskflow.ai.dto.ConversationSummary;
import com.taskflow.ai.dto.DailyPlan;
import com.taskflow.ai.memory.MemoryProperties;
import com.taskflow.entity.AiChatMessage;
import com.taskflow.entity.AiConversation;
import com.taskflow.repository.AiChatMessageRepository;
import com.taskflow.repository.AiConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Issues AI conversation ids and checks who owns them. The browser never invents conversation ids: it asks for one,
 * and every request is checked against the authenticated caller before any agent, chat memory or transcript is
 * touched. Since Phase 3.4 it also keeps the transcript, lists and deletes conversations, and applies retention.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ConversationService {

    public static final String MODE_AGENT = "agent";
    public static final String MODE_SUPERVISOR = "supervisor";
    /** Most conversations returned by {@link #list}. */
    static final int LIST_LIMIT = 50;

    private final AiConversationRepository repository;
    private final AiChatMessageRepository messages;
    private final MemoryProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ConversationService(AiConversationRepository repository, AiChatMessageRepository messages,
                               MemoryProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.messages = messages;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** A new supervisor conversation. */
    @Transactional
    public ConversationRef create(UUID ownerId) {
        return create(ownerId, MODE_SUPERVISOR);
    }

    /**
     * Starts a conversation and keeps the owner within {@code taskflow.ai.memory.max-conversations}: the least
     * recently used conversations beyond the limit are deleted with their memory and transcript.
     */
    @Transactional
    public ConversationRef create(UUID ownerId, String mode) {
        AiConversation conversation = repository.saveAndFlush(
                new AiConversation(ownerId, normalizeMode(mode), Instant.now(clock)));
        List<UUID> beyondLimit = new ArrayList<>(repository.findIdsBeyondNewest(ownerId, properties.maxConversations()));
        if (!beyondLimit.isEmpty()) {
            beyondLimit.remove(conversation.getId());
            if (!beyondLimit.isEmpty()) {
                repository.deleteAllByIdIn(beyondLimit);
                log.info("conversation.limit owner={} deleted={}", ownerId, beyondLimit.size());
            }
        }
        return new ConversationRef(ownerId, conversation.getId());
    }

    /**
     * @throws ConversationNotFoundException if the id is malformed, unknown or owned by another user
     */
    public ConversationRef requireOwned(UUID ownerId, String conversationId) {
        return new ConversationRef(ownerId, owned(ownerId, conversationId).getId());
    }

    /** The caller's conversations, most recent first. */
    public List<ConversationSummary> list(UUID ownerId) {
        return repository.findByOwnerIdOrderByUpdatedAtDesc(ownerId, PageRequest.of(0, LIST_LIMIT)).stream()
                .map(ConversationService::summary)
                .toList();
    }

    public ConversationSummary get(UUID ownerId, String conversationId) {
        return summary(owned(ownerId, conversationId));
    }

    /** The transcript of one of the caller's conversations. */
    public List<ConversationMessage> history(UUID ownerId, String conversationId) {
        AiConversation conversation = owned(ownerId, conversationId);
        return messages.findByConversationIdOrderBySeqAsc(conversation.getId()).stream()
                .map(this::message)
                .toList();
    }

    /** Deletes one of the caller's conversations together with its chat memory and transcript. */
    @Transactional
    public void delete(UUID ownerId, String conversationId) {
        repository.delete(owned(ownerId, conversationId));
        log.info("conversation.deleted owner={}", ownerId);
    }

    /** Appends one turn to the transcript, exactly as the user saw it, and records the activity. */
    @Transactional
    public void recordTurn(ConversationRef conversation, String userMessage, String reply, DailyPlan plan,
                           List<String> guardrails) {
        AiConversation entity = repository.findByIdAndOwnerId(conversation.id(), conversation.ownerId())
                .orElseThrow(ConversationNotFoundException::new);
        Instant now = Instant.now(clock);
        messages.save(new AiChatMessage(entity.getId(), AiChatMessage.Role.USER, userMessage, null, null, now));
        messages.save(new AiChatMessage(entity.getId(), AiChatMessage.Role.ASSISTANT, reply, toJson(plan),
                guardrails == null || guardrails.isEmpty() ? null : String.join(",", guardrails), now));
        entity.touch(userMessage, now);
    }

    /**
     * Retention: deletes every conversation without activity for longer than {@code taskflow.ai.memory.retention},
     * with its memory and transcript.
     */
    @Transactional
    public int deleteInactive(Instant now) {
        int deleted = repository.deleteInactiveSince(now.minus(properties.retention()));
        if (deleted > 0) {
            log.info("conversation.retention deleted={} retention={}", deleted, properties.retention());
        }
        return deleted;
    }

    private AiConversation owned(UUID ownerId, String conversationId) {
        return repository.findByIdAndOwnerId(parse(conversationId), ownerId)
                .orElseThrow(ConversationNotFoundException::new);
    }

    private static ConversationSummary summary(AiConversation conversation) {
        return new ConversationSummary(conversation.getId().toString(), conversation.getTitle(),
                conversation.getMode(), conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private ConversationMessage message(AiChatMessage message) {
        return new ConversationMessage(message.getRole().name().toLowerCase(Locale.ROOT), message.getContent(),
                message.getCreatedAt(), fromJson(message.getPlan()),
                message.getGuardrails() == null ? List.of() : Arrays.asList(message.getGuardrails().split(",")));
    }

    private String toJson(DailyPlan plan) {
        if (plan == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException ex) {
            log.warn("conversation.plan-not-stored error={}", ex.toString());
            return null;
        }
    }

    /** Stored JSON is data: a plan that cannot be read is left out of the history, not fatal. */
    private DailyPlan fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DailyPlan.class);
        } catch (JsonProcessingException ex) {
            log.warn("conversation.plan-unreadable error={}", ex.toString());
            return null;
        }
    }

    private static String normalizeMode(String mode) {
        return MODE_AGENT.equalsIgnoreCase(mode) ? MODE_AGENT : MODE_SUPERVISOR;
    }

    private static UUID parse(String conversationId) {
        if (conversationId == null) {
            throw new ConversationNotFoundException();
        }
        try {
            return UUID.fromString(conversationId.trim());
        } catch (IllegalArgumentException ex) {
            throw new ConversationNotFoundException();
        }
    }
}
