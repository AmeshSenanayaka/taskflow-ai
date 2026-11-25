package com.taskflow.ai.memory;

import com.taskflow.ai.guardrail.GroundedAnswerOutputGuardrail;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    /**
     * Starts staging the memory writes of the turn on this thread.
     *
     * @param owner the owner of the conversation, already verified against the signed-in caller
     *              ({@code ConversationService.requireOwned}); memory of any other owner is refused during the turn
     */
    public void begin(UUID owner) {
        StagedMemory.begin(owner);
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
                store.write(entry.key(), withoutRepairs(entry.messages(), AgentRunRecorder.repairs()));
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

    static List<ChatMessage> withoutRepairs(List<ChatMessage> messages) {
        return withoutRepairs(messages, Map.of());
    }

    /**
     * Replaces the answers an output guardrail rejected with the repaired answers, so memory holds only answers that
     * passed the checks.
     * <p>
     * LangChain4j (1.23) adds the agent's answer to chat memory before the output guardrail runs, sends the reprompt
     * to the model without storing it, and does not store the repaired answer: memory keeps the rejected draft. The
     * guardrail therefore records each repair ({@code AgentRunRecorder.repairs()}: draft to repaired answer), and the
     * draft is replaced here. As a second line of defence, a text answer directly followed by another text answer is
     * treated as a superseded draft (a tool-calling AiMessage is always followed by tool results), and a stored
     * reprompt is removed.
     */
    static List<ChatMessage> withoutRepairs(List<ChatMessage> messages, Map<String, String> repairs) {
        List<ChatMessage> kept = new ArrayList<>(messages.size());
        for (ChatMessage original : messages) {
            ChatMessage message = original;
            if (message instanceof AiMessage ai && !ai.hasToolExecutionRequests() && ai.text() != null
                    && repairs.containsKey(ai.text())) {
                message = AiMessage.from(repairs.get(ai.text()));
            }
            if (message instanceof UserMessage user && user.hasSingleText()
                    && user.singleText().contains(GroundedAnswerOutputGuardrail.REPROMPT_MARKER)) {
                continue;
            }
            if (isTextAnswer(message) && !kept.isEmpty() && isTextAnswer(kept.get(kept.size() - 1))) {
                kept.remove(kept.size() - 1);
            }
            kept.add(message);
        }
        return kept;
    }

    private static boolean isTextAnswer(ChatMessage message) {
        return message instanceof AiMessage ai && !ai.hasToolExecutionRequests();
    }
}
