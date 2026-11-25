package com.taskflow.ai.memory;

import java.util.UUID;

/**
 * The parsed LangChain4j {@code @MemoryId}: {@code ownerId:conversationId} (see {@code ConversationRef.memoryKey()}).
 * Every memory read and write goes through this, so a key that does not carry an owner is refused.
 */
public record MemoryKey(UUID ownerId, UUID conversationId) {

    public static MemoryKey parse(Object memoryId) {
        String text = String.valueOf(memoryId);
        int separator = text.indexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException("Chat memory id must be ownerId:conversationId");
        }
        try {
            return new MemoryKey(UUID.fromString(text.substring(0, separator)),
                    UUID.fromString(text.substring(separator + 1)));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Chat memory id must be ownerId:conversationId", ex);
        }
    }

    @Override
    public String toString() {
        return ownerId + ":" + conversationId;
    }
}
