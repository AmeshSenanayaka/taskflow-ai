package com.taskflow.controller;
import com.taskflow.ai.agent.TaskAgent;
import com.taskflow.ai.exception.AiServiceException;
import com.taskflow.config.OpenApiConfig;
import dev.langchain4j.service.Result;
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

/**
 * HTTP surface of the AI assistant. It only validates input and delegates to {@link com.taskflow.ai.agent.TaskAgent};
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

    @Value("${langchain4j.open-ai.chat-model.model-name:unknown}")
    private String modelName;

    /** Lets the UI show whether the assistant is reachable and which model answers. */
    @GetMapping("/status")
    public AiStatus status() {
        return new AiStatus(true, modelName, null);
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request
    ) {
        Result<String> response;
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
                        response.content()
                )
        );
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
