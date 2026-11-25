package com.taskflow.ai.memory;

import dev.langchain4j.data.message.ChatMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The memory writes of the turn running on this thread, held back until the turn is known to be good
 * (Phase 3.4 "staged memory commits").
 * <p>
 * LangChain4j writes chat memory as it goes: the user message, tool calls, tool results, the answer, and also
 * guardrail reprompts and the answers they rejected. While a turn is staged, {@link PostgresChatMemoryStore} writes
 * here instead of to the database and reads its own staged writes back, so the agents behave exactly as before.
 * {@link ConversationMemory} then commits the turn when it succeeded, or discards it when the answer was rejected
 * or the model failed. The supervisor runs every agent on the request thread, so a thread-local buffer sees all of
 * them.
 */
final class StagedMemory {

    /** A staged window; {@code messages == null} means "deleted". */
    record Entry(String namespace, MemoryKey key, List<ChatMessage> messages) {
    }

    private static final ThreadLocal<Map<String, Entry>> CURRENT = new ThreadLocal<>();

    private StagedMemory() {
    }

    static void begin() {
        CURRENT.set(new LinkedHashMap<>());
    }

    static boolean active() {
        return CURRENT.get() != null;
    }

    /** @return the staged window, an empty optional when nothing is staged for it (read the database) */
    static Optional<Entry> get(String namespace, MemoryKey key) {
        Map<String, Entry> staged = CURRENT.get();
        return staged == null ? Optional.empty() : Optional.ofNullable(staged.get(id(namespace, key)));
    }

    static void put(String namespace, MemoryKey key, List<ChatMessage> messages) {
        CURRENT.get().put(id(namespace, key), new Entry(namespace, key, messages == null ? null : List.copyOf(messages)));
    }

    /** Ends staging on this thread and returns what was staged, in write order. */
    static List<Entry> end() {
        Map<String, Entry> staged = CURRENT.get();
        CURRENT.remove();
        return staged == null ? List.of() : List.copyOf(staged.values());
    }

    private static String id(String namespace, MemoryKey key) {
        return namespace + "|" + key;
    }
}
