package com.taskflow.ai.memory;

import com.taskflow.ai.guardrail.GroundedAnswerOutputGuardrail;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The parts of persistent memory that need no database: keys, reprompt removal, the size cap. */
class ConversationMemoryTest {

    @Test
    void aMemoryIdWithoutAnOwnerIsRefused() {
        UUID owner = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();

        assertThat(MemoryKey.parse(owner + ":" + conversation)).isEqualTo(new MemoryKey(owner, conversation));
        assertThatThrownBy(() -> MemoryKey.parse(conversation.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemoryKey.parse("conv-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCommittedTurnKeepsTheRepairedAnswerButNotTheRepromptOrTheRejectedDraft() {
        List<ChatMessage> turn = List.of(
                UserMessage.from("Show my open tasks"),
                AiMessage.from("Your tasks: \"Book flights to Tokyo\"."),
                UserMessage.from(GroundedAnswerOutputGuardrail.REPROMPT_MARKER + "\n- invented title"),
                AiMessage.from("Your tasks: \"Prepare sprint review\"."));

        assertThat(ConversationMemory.withoutRepairs(turn)).extracting(Object::toString)
                .noneMatch(text -> text.contains("Tokyo"))
                .noneMatch(text -> text.contains("automatic checks"))
                .anyMatch(text -> text.contains("Prepare sprint review"));
        assertThat(ConversationMemory.withoutRepairs(turn)).hasSize(2);
    }

    @Test
    void theRejectedDraftLangChain4jKeepsInMemoryIsRemoved() {
        // What LangChain4j 1.23 stores after a reprompt: the draft, then the repaired answer (no reprompt message).
        List<ChatMessage> turn = List.of(
                UserMessage.from("Show my open tasks"),
                AiMessage.from("Your tasks: \"Book flights to Tokyo\"."),
                AiMessage.from("Your tasks: \"Prepare sprint review\"."));

        assertThat(ConversationMemory.withoutRepairs(turn)).extracting(Object::toString)
                .noneMatch(text -> text.contains("Tokyo"))
                .anyMatch(text -> text.contains("Prepare sprint review"));
    }

    @Test
    void theRejectedDraftIsReplacedByTheRepairedAnswerLangChain4jNeverStored() {
        // What LangChain4j 1.23 really stores after a reprompt: the draft only; the repaired answer is not stored.
        String draft = "Your tasks: \"Book flights to Tokyo\".";
        String repaired = "Your tasks: \"Prepare sprint review\".";
        List<ChatMessage> turn = List.of(UserMessage.from("Show my open tasks"), AiMessage.from(draft));

        assertThat(ConversationMemory.withoutRepairs(turn, java.util.Map.of(draft, repaired)))
                .containsExactly(UserMessage.from("Show my open tasks"), AiMessage.from(repaired));
    }

    @Test
    void aToolCallFollowedByItsResultIsKept() {
        ToolExecutionRequest call = ToolExecutionRequest.builder().id("1").name("listTasks").arguments("{}").build();
        List<ChatMessage> turn = List.of(UserMessage.from("Show my tasks"), AiMessage.from(List.of(call)),
                ToolExecutionResultMessage.from(call, "[]"), AiMessage.from("You have no tasks."));

        assertThat(ConversationMemory.withoutRepairs(turn)).isEqualTo(turn);
    }

    @Test
    void anOversizedWindowDropsTheOldestMessagesButKeepsTheSystemMessageAndNoOrphanToolResults() {
        ToolExecutionRequest call = ToolExecutionRequest.builder().id("1").name("listTasks").arguments("{}").build();
        List<ChatMessage> window = List.of(
                SystemMessage.from("You are TaskAgent."),
                AiMessage.from(List.of(call)),
                ToolExecutionResultMessage.from(call, "x".repeat(4000)),
                UserMessage.from("latest question"),
                AiMessage.from("latest answer"));

        String json = PostgresChatMemoryStore.MemoryHygiene.toJson(window, 1500);
        List<ChatMessage> kept = ChatMessageDeserializer.messagesFromJson(json);

        assertThat(json.length()).isLessThanOrEqualTo(1500);
        assertThat(kept.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(kept).noneMatch(message -> message instanceof ToolExecutionResultMessage);
        assertThat(kept.get(kept.size() - 1)).isEqualTo(AiMessage.from("latest answer"));
    }
}
