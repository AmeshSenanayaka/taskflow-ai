package com.taskflow.ai.guardrail;

import com.taskflow.ai.orchestration.AgentRunRecorder;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.TaskService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.taskflow.ai.guardrail.ConversationGuardrailsTest.EMAILS;
import static com.taskflow.ai.guardrail.ConversationGuardrailsTest.REVIEW;
import static com.taskflow.ai.guardrail.ConversationGuardrailsTest.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The guardrails built on LangChain4j's InputGuardrail / OutputGuardrail API. */
@ExtendWith(MockitoExtension.class)
class LangChain4jGuardrailsTest {

    @Mock
    private TaskService taskService;
    @Mock
    private CurrentUserProvider currentUser;

    private AgentRunRecorder.Run run;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.userId()).thenReturn(USER);
        run = AgentRunRecorder.open();
    }

    @AfterEach
    void tearDown() {
        AgentRunRecorder.close();
    }

    @Test
    void theInputGuardrailRefusesOffTopicMessagesOnThePhase1Path() {
        InputGuardrailResult result = new TaskScopeInputGuardrail().validate(UserMessage.from("Write me a poem"));

        assertThat(result.result()).isEqualTo(GuardrailResult.Result.FAILURE);
        assertThat(run.guardrails()).containsExactly(GuardrailCodes.INPUT_OUT_OF_SCOPE);
    }

    @Test
    void theInputGuardrailStandsDownWhenTheEntryPointAlreadyCheckedTheMessage() {
        AgentRunRecorder.markInputChecked();

        assertThat(new TaskScopeInputGuardrail().validate(UserMessage.from("Write me a poem")).result())
                .isEqualTo(GuardrailResult.Result.SUCCESS);
    }

    @Test
    void anInventedTaskIsSentBackToTheAgentWithTheFactsItMayUse() {
        when(taskService.listAll(USER)).thenReturn(List.of(REVIEW, EMAILS));
        GroundedAnswerOutputGuardrail guardrail = new GroundedAnswerOutputGuardrail(taskService, currentUser);

        OutputGuardrailResult first = guardrail.validate(request(
                "You have \"Prepare sprint review\" and \"Book flights to Tokyo\"."));

        assertThat(first.isReprompt()).isTrue();
        assertThat(first.getReprompt()).get().asString()
                .contains("\"Prepare sprint review\"").contains("\"Reply to customer emails\"");
        assertThat(run.guardrails()).containsExactly(GuardrailCodes.OUTPUT_UNKNOWN_TASK);

        OutputGuardrailResult second = guardrail.validate(request(
                "You have \"Prepare sprint review\" and \"Reply to customer emails\"."));

        assertThat(second.result()).isEqualTo(GuardrailResult.Result.SUCCESS);
        assertThat(run.guardrails()).containsExactly(GuardrailCodes.OUTPUT_UNKNOWN_TASK, GuardrailCodes.OUTPUT_REWRITTEN);
    }

    @Test
    void theGuardrailsOwnRepromptDoesNotWhitelistTheInventedTitle() {
        when(taskService.listAll(USER)).thenReturn(List.of(REVIEW, EMAILS));
        GroundedAnswerOutputGuardrail guardrail = new GroundedAnswerOutputGuardrail(taskService, currentUser);
        String invented = "You have \"Prepare sprint review\" and \"Book flights to Tokyo\".";
        OutputGuardrailResult first = guardrail.validate(request(invented));

        // LangChain4j sends the reprompt as a new user message; it quotes "Book flights to Tokyo" as the problem.
        OutputGuardrailResult second = guardrail.validate(request(invented,
                UserMessage.from("Summarise my open tasks"), UserMessage.from(first.getReprompt().orElseThrow())));

        assertThat(second.isReprompt()).isTrue();
        assertThat(run.guardrails()).doesNotContain(GuardrailCodes.OUTPUT_REWRITTEN);
    }

    private static OutputGuardrailRequest request(String answer) {
        return request(answer, UserMessage.from("Summarise my open tasks"));
    }

    private static OutputGuardrailRequest request(String answer, UserMessage... messages) {
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder().aiMessage(AiMessage.from(answer)).build())
                // Not used by the guardrail; LangChain4j's executor uses it to send the reprompt.
                .chatExecutor(mock(ChatExecutor.class))
                .requestParams(mock(GuardrailRequestParams.class))
                .chatRequest(ChatRequest.builder().messages(messages).build())
                .build();
    }
}
