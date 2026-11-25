package com.taskflow.ai.memory;

import com.taskflow.security.CurrentUserProvider;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LangChain4j {@link ChatMemoryStore} backed by PostgreSQL, one instance per agent {@link MemoryNamespace}
 * (Phase 3.4). Memory survives restarts and is isolated three ways:
 * <ul>
 *   <li><b>per user</b>: the memory id must belong to the signed-in caller, and the SQL checks the conversation's
 *       owner as well; either mismatch fails closed;</li>
 *   <li><b>per conversation</b>: rows are keyed by conversation id;</li>
 *   <li><b>per agent</b>: the namespace is part of the key.</li>
 * </ul>
 * While a turn is staged ({@link StagedMemory}) writes are buffered and reads see the buffer; outside a turn they go
 * straight to the database.
 */
@Slf4j
public class PostgresChatMemoryStore implements ChatMemoryStore {

    private final String namespace;
    private final ChatMemoryRepository repository;
    private final CurrentUserProvider currentUser;
    private final Clock clock;
    private final int maxStoredBytes;

    public PostgresChatMemoryStore(String namespace, ChatMemoryRepository repository, CurrentUserProvider currentUser,
                                   Clock clock, int maxStoredBytes) {
        this.namespace = namespace;
        this.repository = repository;
        this.currentUser = currentUser;
        this.clock = clock;
        this.maxStoredBytes = maxStoredBytes;
    }

    public String namespace() {
        return namespace;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        MemoryKey key = authorize(memoryId);
        var staged = StagedMemory.get(namespace, key);
        if (staged.isPresent()) {
            return staged.get().messages() == null ? new ArrayList<>() : new ArrayList<>(staged.get().messages());
        }
        return repository.load(key, namespace).map(json -> read(json, key)).orElseGet(ArrayList::new);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        MemoryKey key = authorize(memoryId);
        if (StagedMemory.active()) {
            StagedMemory.put(namespace, key, messages);
            return;
        }
        write(key, messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        MemoryKey key = authorize(memoryId);
        if (StagedMemory.active()) {
            StagedMemory.put(namespace, key, null);
            return;
        }
        repository.delete(key, namespace);
    }

    /** Writes a window to the database (used directly outside a turn and by {@link ConversationMemory#commit}). */
    void write(MemoryKey key, List<ChatMessage> messages) {
        if (!repository.save(key, namespace, MemoryHygiene.toJson(messages, maxStoredBytes), Instant.now(clock))) {
            throw new IllegalStateException("Chat memory " + key.conversationId() + " is not owned by " + key.ownerId());
        }
    }

    void delete(MemoryKey key) {
        repository.delete(key, namespace);
    }

    /**
     * The memory id must name the signed-in caller as owner; anything else is a bug or an attack. During a staged
     * turn the owner was verified once when the turn started, so the check needs no lookup of the current user.
     */
    private MemoryKey authorize(Object memoryId) {
        MemoryKey key = MemoryKey.parse(memoryId);
        UUID owner = StagedMemory.owner() != null ? StagedMemory.owner() : currentUser.userId();
        if (!key.ownerId().equals(owner)) {
            throw new IllegalStateException("Chat memory of another user requested");
        }
        return key;
    }

    /** Stored JSON is data from the database: a window that cannot be read is dropped, not trusted or fatal. */
    private List<ChatMessage> read(String json, MemoryKey key) {
        try {
            return new ArrayList<>(ChatMessageDeserializer.messagesFromJson(json));
        } catch (RuntimeException ex) {
            log.warn("memory.unreadable conversation={} namespace={} error={}", key.conversationId(), namespace,
                    ex.toString());
            return new ArrayList<>();
        }
    }

    /** Serialization with the size cap, kept apart so it is testable without a database. */
    static final class MemoryHygiene {

        private MemoryHygiene() {
        }

        static String toJson(List<ChatMessage> messages, int maxBytes) {
            List<ChatMessage> kept = new ArrayList<>(messages);
            String json = ChatMessageSerializer.messagesToJson(kept);
            // Drop the oldest non-system messages until the window fits; the system message always stays.
            int first = !kept.isEmpty() && kept.get(0) instanceof SystemMessage ? 1 : 0;
            while (json.length() > maxBytes && kept.size() > first + 1) {
                kept.remove(first);
                // A tool result whose tool call was dropped would be rejected by the model provider: drop it too.
                while (kept.size() > first && kept.get(first) instanceof ToolExecutionResultMessage) {
                    kept.remove(first);
                }
                json = ChatMessageSerializer.messagesToJson(kept);
            }
            return json;
        }
    }
}
