package com.taskflow.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/**
 * The Phase 1 agent, declared as a LangChain4j AI Service. LangChain4j implements this interface
 * at runtime: it renders the system prompt, loads the conversation memory for {@code conversationId},
 * sends the request to the chat model and runs the tool-calling loop against {@code TaskTools}.
 * <p>
 * The same interface serves two roles:
 * <ul>
 *   <li>Phase 1: the Spring starter builds it as an {@code @AiService} bean behind {@code POST /api/ai/chat}.</li>
 *   <li>Phase 2: {@code TaskFlowAgentFactory} builds a second instance with {@code AgenticServices} and hands it
 *       to the {@link TaskFlowSupervisor} as a sub-agent. The {@link Agent} metadata (name, description,
 *       {@code outputKey}) is what the supervisor's planner sees when it decides whom to call.</li>
 * </ul>
 * {@code @MemoryId} keeps one chat memory per conversation in both roles, so follow-ups such as
 * "change its priority to LOW" resolve against the previous turn.
 */
@AiService
public interface TaskAgent {

    String NAME = "TaskAgent";
    String OUTPUT_KEY = "taskResult";

    @Agent(
            name = NAME,
            description = """
                    Manages the user's tasks: creates tasks, finds and lists tasks by status, priority,
                    due date or keyword, reads one task, and updates a task's title, description, priority,
                    due date or status (for example marks a task as completed). Use it for any request that
                    reads or changes task data.
                    """,
            outputKey = OUTPUT_KEY
    )
    @SystemMessage(fromResource = "/prompts/task-agent-system.txt")
    @UserMessage("{{request}}")
    String chat(@MemoryId String conversationId,
                @V("request") String request);
}
