package com.taskflow.controller;
import com.taskflow.ai.agent.TaskAgent;
import com.taskflow.ai.dto.AgentDescriptor;
import com.taskflow.ai.dto.SupervisorChatResponse;
import com.taskflow.ai.exception.AiServiceException;
import com.taskflow.ai.service.MultiAgentService;
import com.taskflow.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HTTP surface of the AI assistant. It only validates input and delegates to the Phase 1
 * {@link com.taskflow.ai.agent.TaskAgent} ({@code /chat}) or the Phase 2 {@link MultiAgentService} ({@code /supervisor/chat});
 * no LLM or tool logic lives here.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/ai")
@Tag(name = OpenApiConfig.TAG_AI)
@RequiredArgsConstructor
public class AiChatController {

    private final TaskAgent taskAgentService;
    private final MultiAgentService multiAgentService;

    @Value("${langchain4j.open-ai.chat-model.model-name:unknown}")
    private String modelName;

    /** Lets the UI show whether the assistant is reachable and which model answers. */
    @GetMapping("/status")
    public AiStatus status() {
        return new AiStatus(true, modelName, null);
    }

    @Operation(summary = "Send a message to the single TaskAgent (Phase 1)")
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request
    ) {
        String response;
        try {
            response = taskAgentService.chat(
                    request.conversationId(),
                    request.message()
            );
        } catch (RuntimeException ex) {
            // Tool failures never reach here (LangChain4j feeds them back to the model); this is the model
            // call itself failing: rate limit, timeout, bad key, network. Log the detail, return a safe message.
            AiServiceException translated = AiServiceException.translate(ex);
            log.error("AI chat failed for conversation {} ({}): {}",
                    request.conversationId(), translated.status(), ex.toString());
            throw translated;
        }

        return ResponseEntity.ok(
                new ChatResponse(
                        request.conversationId(),
                        response
                )
        );
    }

    @Operation(summary = "Send a message to the TaskFlowSupervisor (Phase 2)",
            description = "The supervisor decides which specialised agents to call (TaskAgent, PriorityAgent, "
                    + "PlanningAgent, SummaryAgent), runs them and returns the composed answer together with the "
                    + "agents and tools it used.")
    @PostMapping("/supervisor/chat")
    public SupervisorChatResponse supervise(@Valid @RequestBody ChatRequest request) {
        return multiAgentService.chat(request.conversationId(), request.message());
    }

    @Operation(summary = "List the specialised agents available behind the supervisor")
    @GetMapping("/agents")
    public List<AgentDescriptor> agents() {
        return multiAgentService.agents();
    }

    public record ChatRequest(
            @NotBlank(message = "Conversation ID is required")
            String conversationId,

            @NotBlank(message = "Message cannot be empty")
            @Size(max = 4000, message = "Message is too long")
            String message
    ) {}

    public record ChatResponse(
            String conversationId,
            String message
    ) {}

    public record AiStatus(
            boolean available,
            String model,
            String message
    ) {}
}
