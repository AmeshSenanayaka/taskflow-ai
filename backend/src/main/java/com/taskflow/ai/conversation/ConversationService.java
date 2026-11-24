package com.taskflow.ai.conversation;

import com.taskflow.entity.AiConversation;
import com.taskflow.repository.AiConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues AI conversation ids and checks who owns them. The browser no longer invents conversation ids:
 * it asks for one, and every chat request is checked against the authenticated caller before any agent or
 * chat memory is touched.
 */
@Service
@Transactional(readOnly = true)
public class ConversationService {

    private final AiConversationRepository repository;
    private final Clock clock;

    public ConversationService(AiConversationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ConversationRef create(UUID ownerId) {
        AiConversation conversation = repository.save(new AiConversation(ownerId, Instant.now(clock)));
        return new ConversationRef(ownerId, conversation.getId());
    }

    /**
     * @throws ConversationNotFoundException if the id is malformed, unknown or owned by another user
     */
    public ConversationRef requireOwned(UUID ownerId, String conversationId) {
        UUID id = parse(conversationId);
        return repository.findByIdAndOwnerId(id, ownerId)
                .map(conversation -> new ConversationRef(ownerId, conversation.getId()))
                .orElseThrow(ConversationNotFoundException::new);
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
