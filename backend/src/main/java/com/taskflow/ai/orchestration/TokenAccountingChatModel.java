package com.taskflow.ai.orchestration;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.Set;

/**
 * Decorates the provider's {@link ChatModel} so that every call made by any agent of the supervisor
 * system (planner, sub-agents, tool rounds) is counted and its token usage summed into the current
 * {@link AgentRunRecorder} record. Everything else is delegated untouched.
 */
final class TokenAccountingChatModel implements ChatModel {

    private final ChatModel delegate;

    TokenAccountingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        ChatResponse response = delegate.chat(request);
        AgentRunRecorder.recordLlmCall(response == null ? null : response.tokenUsage());
        return response;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return chat(request);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
