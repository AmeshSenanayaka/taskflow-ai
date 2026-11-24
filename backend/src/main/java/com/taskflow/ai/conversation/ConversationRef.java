package com.taskflow.ai.conversation;

import java.util.UUID;

/**
 * An AI conversation whose ownership has been verified for the current caller. Only
 * {@link ConversationService} creates instances, so holding one means "this user may use this conversation".
 *
 * @param ownerId the authenticated user who owns the conversation
 * @param id      the server-issued conversation id the client sends back
 */
public record ConversationRef(UUID ownerId, UUID id) {

    /**
     * The key LangChain4j uses for chat memory ({@code @MemoryId}). It is qualified by the owner, so two users'
     * memories can never be the same entry even if a conversation id were reused or guessed: defence in depth
     * behind the ownership check.
     */
    public String memoryKey() {
        return ownerId + ":" + id;
    }
}
