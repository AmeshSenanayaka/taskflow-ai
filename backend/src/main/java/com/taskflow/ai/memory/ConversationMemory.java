package com.taskflow.ai.memory;

import com.taskflow.ai.guardrail.GroundedAnswerOutputGuardrail;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Staged memory commits (Phase 3.4): a turn's chat-memory writes are kept back and become the conversation's memory
 * only when the turn is good.
 * <pre>
 *   begin()                  before the agents run
 *   commit()                 the answer was shown as written            → the turn becomes memory
 *   discard()                the answer was rejected (fallback), the    → memory stays as it was before the turn
 *                            model failed, or an input guardrail refused
 * </pre>
 * A committed turn never contains the guardrail's reprompts or the answers they rejected: those are removed first.
 */
@Slf4j
public class ConversationMemory {

    private final Map<String, PostgresChatMemoryStore> stores;

    public ConversationMemory(List<PostgresChatMemoryStore> stores) {
        this.stores = stores.stream().collect(Collectors.toMap(PostgresChatMemoryStore::namespace, Function.identity()));
    }

    /** Starts staging the memory writes of the turn on this thread. */
    public void begin() {
        StagedMemory.begin();
    }

    /** Persists the staged turn, without reprompts and the answers they rejected. */
    @Transactional
    public void commit() {
        List<StagedMemory.Entry> staged = StagedMemory.end();
        for (StagedMemory.Entry entry : staged) {
            PostgresChatMemoryStore store = stores.get(entry.namespace());
            if (store == null) {
                continue;
            }
            if (entry.messages() == null) {
                store.delete(entry.key());
            } else {
                store.write(entry.key(), withoutRepairs(entry.messages()));
            }
        }
        log.info("memory.committed windows={}", staged.size());
    }

    /** Drops the staged turn: the conversation's memory stays exactly as it was before the turn. */
    public void discard() {
        List<StagedMemory.Entry> staged = StagedMemory.end();
        if (!staged.isEmpty()) {
            log.info("memory.discarded windows={}", staged.size());
        }
    }

    /**
     * Removes each output-guardrail reprompt and the rejected answer just before it. Only the repaired answer, which
     * passed the checks, remains.
     */
    static List<ChatMessage> withoutRepairs(List<ChatMessage> messages) {
        List<ChatMessage> kept = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage user && user.hasSingleText()
                    && user.singleText().contains(GroundedAnswerOutputGuardrail.REPROMPT_MARKER)) {
                if (!kept.isEmpty() && kept.get(kept.size() - 1) instanceof AiMessage) {
                    kept.remove(kept.size() - 1);
                }
                continue;
            }
            kept.add(message);
        }
        return kept;
    }
}
