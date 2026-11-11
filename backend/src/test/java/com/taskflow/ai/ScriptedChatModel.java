package com.taskflow.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link ChatModel} test double that replays scripted answers in order, so the agent loops
 * (planner -> sub-agent -> tool -> sub-agent -> planner) can be tested deterministically without any
 * LLM provider. Every request is recorded for assertions on prompts, memory and tool results.
 */
public class ScriptedChatModel implements ChatModel {

    private final Deque<Function<ChatRequest, ChatResponse>> script = new ArrayDeque<>();
    private final List<ChatRequest> requests = Collections.synchronizedList(new ArrayList<>());

    public ScriptedChatModel reset() {
        script.clear();
        requests.clear();
        return this;
    }

    /** Next call answers with plain text. */
    public ScriptedChatModel text(String text) {
        script.add(request -> response(AiMessage.from(text)));
        return this;
    }

    /** Next call asks for a tool execution. */
    public ScriptedChatModel toolCall(String toolName, String argumentsJson) {
        script.add(request -> response(AiMessage.from(ToolExecutionRequest.builder()
                .id("call-" + requests.size())
                .name(toolName)
                .arguments(argumentsJson)
                .build())));
        return this;
    }

    /** Next call is the supervisor's planner choosing a sub-agent. */
    public ScriptedChatModel invokeAgent(String agentName, String request) {
        return text("{\"agentName\":\"" + agentName + "\",\"arguments\":{\"request\":" + quote(request) + "}}");
    }

    /** Next call is the supervisor's planner finishing with a recap. */
    public ScriptedChatModel done(String response) {
        return text("{\"agentName\":\"done\",\"arguments\":{\"response\":" + quote(response) + "}}");
    }

    /** Next call fails as the provider would. */
    public ScriptedChatModel failWith(RuntimeException error) {
        script.add(request -> {
            throw error;
        });
        return this;
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    public int remainingScript() {
        return script.size();
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        Function<ChatRequest, ChatResponse> next = script.poll();
        if (next == null) {
            throw new IllegalStateException("ScriptedChatModel: no scripted answer left for request #" + requests.size()
                    + " (last message: " + describeLast(request) + ")");
        }
        return next.apply(request);
    }

    // ---- helpers for assertions ----

    public static String systemMessage(ChatRequest request) {
        return request.messages().stream()
                .filter(SystemMessage.class::isInstance)
                .map(message -> ((SystemMessage) message).text())
                .findFirst()
                .orElse("");
    }

    public static Optional<String> lastUserMessage(ChatRequest request) {
        return request.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(message -> ((UserMessage) message).singleText())
                .reduce((first, second) -> second);
    }

    public static Optional<String> lastToolResult(ChatRequest request) {
        return request.messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(message -> ((ToolExecutionResultMessage) message).text())
                .reduce((first, second) -> second);
    }

    public static List<String> toolNames(ChatRequest request) {
        return request.toolSpecifications() == null ? List.of()
                : request.toolSpecifications().stream().map(spec -> spec.name()).toList();
    }

    private static ChatResponse response(AiMessage message) {
        return ChatResponse.builder()
                .aiMessage(message)
                .tokenUsage(new TokenUsage(100, 20))
                .finishReason(message.hasToolExecutionRequests() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP)
                .build();
    }

    private static String describeLast(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        return messages.isEmpty() ? "none" : messages.get(messages.size() - 1).toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
