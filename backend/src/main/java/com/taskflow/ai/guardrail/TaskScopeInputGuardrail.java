package com.taskflow.ai.guardrail;

import com.taskflow.ai.orchestration.AgentRunRecorder;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Optional;

/**
 * LangChain4j input guardrail declared with {@code @InputGuardrails} on {@code TaskAgent.chat} (Phase 3.3).
 * <p>
 * For the Phase 1 endpoint, where {@code TaskAgent} receives the user's own message, this is the input guardrail.
 * In the multi-agent path the same interface is a sub-agent that receives the planner's request, not the user's
 * message; the user's message was already checked once at the entry point ({@link ConversationGuardrails}), so
 * this guardrail lets it through rather than second-guessing the planner's wording.
 * <p>
 * Instantiated by LangChain4j through its no-argument constructor, so it holds no Spring dependencies.
 */
public class TaskScopeInputGuardrail implements InputGuardrail {

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (AgentRunRecorder.inputChecked()) {
            return success();
        }
        String text = userMessage.hasSingleText() ? userMessage.singleText() : userMessage.toString();
        Optional<ConversationGuardrails.Refusal> refusal = ConversationGuardrails.classifyInput(text);
        if (refusal.isEmpty()) {
            return success();
        }
        AgentRunRecorder.recordGuardrail(refusal.get().code());
        return failure(refusal.get().code());
    }
}
