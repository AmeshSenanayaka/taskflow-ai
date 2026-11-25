package com.taskflow.ai.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SQL for {@code ai_chat_memory}. Every statement joins or checks {@code ai_conversations.owner_id}, so a memory key
 * whose owner does not own the conversation reads nothing and writes nothing (fails closed).
 */
@Repository
public class ChatMemoryRepository {

    private final JdbcTemplate jdbc;

    public ChatMemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> load(MemoryKey key, String namespace) {
        List<String> rows = jdbc.queryForList("""
                SELECT m.messages FROM ai_chat_memory m
                JOIN ai_conversations c ON c.id = m.conversation_id
                WHERE m.conversation_id = ? AND m.namespace = ? AND c.owner_id = ?
                """, String.class, key.conversationId(), namespace, key.ownerId());
        return rows.stream().findFirst();
    }

    /** @return false when the conversation does not exist or belongs to someone else (nothing was written) */
    public boolean save(MemoryKey key, String namespace, String json, Instant now) {
        int rows = jdbc.update("""
                INSERT INTO ai_chat_memory (conversation_id, namespace, messages, updated_at)
                SELECT c.id, ?, ?, ? FROM ai_conversations c WHERE c.id = ? AND c.owner_id = ?
                ON CONFLICT (conversation_id, namespace)
                DO UPDATE SET messages = EXCLUDED.messages, updated_at = EXCLUDED.updated_at
                """, namespace, json, Timestamp.from(now), key.conversationId(), key.ownerId());
        return rows > 0;
    }

    public void delete(MemoryKey key, String namespace) {
        jdbc.update("""
                DELETE FROM ai_chat_memory m USING ai_conversations c
                WHERE m.conversation_id = c.id AND m.conversation_id = ? AND m.namespace = ? AND c.owner_id = ?
                """, key.conversationId(), namespace, key.ownerId());
    }

    /** Every stored window of a conversation, for evaluations and tests that inspect what was kept. */
    public List<String> loadAll(MemoryKey key) {
        return jdbc.queryForList("""
                SELECT m.messages FROM ai_chat_memory m
                JOIN ai_conversations c ON c.id = m.conversation_id
                WHERE m.conversation_id = ? AND c.owner_id = ?
                ORDER BY m.namespace
                """, String.class, key.conversationId(), key.ownerId());
    }
}
