package com.taskflow.controller;
import com.taskflow.ai.agent.TaskAgent;
import com.taskflow.ai.conversation.ConversationRef;
import com.taskflow.ai.conversation.ConversationService;
import com.taskflow.ai.dto.AgentDescriptor;
import com.taskflow.ai.dto.ConversationMessage;
import com.taskflow.ai.dto.ConversationSummary;
import com.taskflow.ai.dto.SupervisorChatResponse;
import com.taskflow.ai.exception.AiServiceException;
import com.taskflow.ai.guardrail.ConversationGuardrails;
import com.taskflow.ai.guardrail.GuardrailCodes;
import com.taskflow.ai.memory.ConversationMemory;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import com.taskflow.ai.service.MultiAgentService;
import com.taskflow.config.OpenApiConfig;
import com.taskflow.security.CurrentUserProvider;
import dev.langchain4j.guardrail.InputGuardrailException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HTTP surface of the AI assistant. It only validates input and delegates to the Phase 1
 * {@link com.taskflow.ai.agent.TaskAgent} ({@code /chat}) or the Phase 2 {@link MultiAgentService} ({@code /supervisor/chat});
 * no LLM or tool logic lives here.
 * <p>
 * Phase 3.0: conversation ids are issued by the server ({@code POST /api/ai/conversations}) and every chat request
 * is checked against the authenticated caller before any agent or chat memory is touched. The agents receive an
 * owner-qualified memory key ({@link ConversationRef#memoryKey()}), never the raw id from the request.
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
    private final ConversationService conversations;
    private final CurrentUserProvider currentUser;
    private final ConversationMemory memory;

    @Value("${langchain4j.open-ai.chat-model.model-name:unknown}")
    private String modelName;

    /** Lets the UI show whether the assistant is reachable and which model answers. */
    @GetMapping("/status")
    public AiStatus status() {
        return new AiStatus(true, modelName, null);
    }

    @Operation(summary = "Start a new conversation",
            description = "Returns a server-issued conversation id owned by the caller. Send it with every chat message. "
                    + "mode is agent (Phase 1 TaskAgent) or supervisor (default); the two keep separate memory.")
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createConversation(
            @RequestParam(name = "mode", defaultValue = ConversationService.MODE_SUPERVISOR) String mode) {
        ConversationRef conversation = conversations.create(currentUser.userId(), mode);
        return new ConversationResponse(conversation.id().toString());
    }

    @Operation(summary = "List the caller's conversations (Phase 3.4)",
            description = "Most recent activity first, at most 50. Conversations are deleted after the retention period.")
    @GetMapping("/conversations")
    public List<ConversationSummary> listConversations() {
        return conversations.list(currentUser.userId());
    }

    @Operation(summary = "The transcript of one of the caller's conversations (Phase 3.4)",
            description = "404 for an unknown conversation or one owned by another user.")
    @GetMapping("/conversations/{conversationId}/messages")
    public List<ConversationMessage> conversationMessages(@PathVariable String conversationId) {
        return conversations.history(currentUser.userId(), conversationId);
    }

    @Operation(summary = "Delete one of the caller's conversations with its memory and transcript (Phase 3.4)",
            description = "404 for an unknown conversation or one owned by another user.")
    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable String conversationId) {
        conversations.delete(currentUser.userId(), conversationId);
    }

    @Operation(summary = "Send a message to the single TaskAgent (Phase 1)")
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request
    ) {
        // Ownership first: an unknown or foreign conversation is a 404 and never reaches the model.
        ConversationRef conversation = conversations.requireOwned(currentUser.userId(), request.conversationId());
        String response;
        // Recording the request lets the write guardrail count task changes on this path too, and lets the
        // input guardrail report which check refused the message.
        AgentRunRecorder.Run run = AgentRunRecorder.open();
        // Phase 3.4: memory writes are staged and committed only when the turn succeeds.
        memory.begin(conversation.ownerId());
        try {
            response = taskAgentService.chat(
                    conversation.memoryKey(),
                    request.message()
            );
            try {
                memory.commit();
            } catch (RuntimeException ex) {
                // The user still gets the answer; the model forgets the turn (see MultiAgentService.commitMemory).
                log.error("memory.commit-failed error={}", ex.toString(), ex);
            }
            conversations.recordTurn(conversation, request.message(), response, null, run.guardrails());
        } catch (InputGuardrailException ex) {
            // Phase 3.3: TaskAgent's @InputGuardrails(TaskScopeInputGuardrail.class) refused the message.
            // Phase 3.4: nothing of the refused turn is kept in memory; the transcript shows the refusal.
            String code = run.guardrails().isEmpty() ? GuardrailCodes.INPUT_OUT_OF_SCOPE : run.guardrails().get(0);
            log.info("guardrail.input code={}", code);
            String reply = ConversationGuardrails.replyFor(code);
            memory.discard();
            conversations.recordTurn(conversation, request.message(), reply, null, List.of(code));
            return ResponseEntity.ok(new ChatResponse(conversation.id().toString(), reply));
        } catch (RuntimeException ex) {
            // Tool failures never reach here (LangChain4j feeds them back to the model); this is the model
            // call itself failing: rate limit, timeout, bad key, network. Log the detail, return a safe message.
            AiServiceException translated = AiServiceException.translate(ex);
            log.error("AI chat failed for conversation {} ({}): {}",
                    request.conversationId(), translated.status(), ex.toString());
            throw translated;
        } finally {
            memory.discard();
            AgentRunRecorder.close();
        }

        return ResponseEntity.ok(
                new ChatResponse(
                        conversation.id().toString(),
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
        ConversationRef conversation = conversations.requireOwned(currentUser.userId(), request.conversationId());
        return multiAgentService.chat(conversation, request.message());
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

    public record ConversationResponse(String conversationId) {}

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
