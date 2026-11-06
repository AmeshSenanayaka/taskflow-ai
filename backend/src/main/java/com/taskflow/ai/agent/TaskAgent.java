package com.taskflow.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * The Phase 1 agent, declared as a LangChain4j AI Service. LangChain4j implements this interface
 * at runtime: it renders the system prompt, loads the conversation memory for {@code conversationId},
 * sends the request to the chat model and runs the tool-calling loop against {@code TaskTools}.
 * <p>
 * Phase 2 can reuse this exact interface as a specialised agent under a supervisor.
 */
@AiService
public interface TaskAgent {

    @SystemMessage(fromResource = "/prompts/task-agent-system.txt")
    Result<String> chat(@MemoryId String conversationId,
                        @UserMessage String message);
}
