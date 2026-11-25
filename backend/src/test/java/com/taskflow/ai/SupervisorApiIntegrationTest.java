package com.taskflow.ai;

import com.jayway.jsonpath.JsonPath;
import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.UserAccountService;
import com.taskflow.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.taskflow.support.TestUsers.alice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over HTTP with the real Spring wiring, a real PostgreSQL and the scripted model:
 * the supervisor routes to the TaskAgent, whose tool writes through TaskService to the database.
 * Every request runs as the demo user alice with a server-issued conversation id (Phase 3.0).
 */
class SupervisorApiIntegrationTest extends AbstractAiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptedChatModel model;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserAccountService userAccounts;

    private UUID aliceId;

    @BeforeEach
    void reset() {
        model.reset();
        taskRepository.deleteAll();
        aliceId = userAccounts.resolve(TestUsers.ALICE_SUBJECT, "alice").id();
    }

    @Test
    void supervisorRoutesToTaskAgentWhichCreatesATaskInTheDatabase() throws Exception {
        String conversation = newConversation();
        model.invokeAgent("TaskAgent", "Create a high-priority task to review the architecture document on 2099-11-14")
                .toolCall("createTask", """
                        {"title":"Review the architecture document","priority":"HIGH","dueDate":"2099-11-14"}
                        """)
                .text("Created \"Review the architecture document\" (HIGH), due 2099-11-14.")
                .done("Created the task.");

        mockMvc.perform(post("/api/ai/supervisor/chat").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s",
                                 "message":"Create a high-priority task to review the architecture document on 2099-11-14"}
                                """.formatted(conversation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversation))
                .andExpect(jsonPath("$.message").value("Created \"Review the architecture document\" (HIGH), due 2099-11-14."))
                .andExpect(jsonPath("$.dataChanged").value(true))
                .andExpect(jsonPath("$.execution.requestId").isNotEmpty())
                .andExpect(jsonPath("$.execution.agents", hasSize(1)))
                .andExpect(jsonPath("$.execution.agents[0].name").value("TaskAgent"))
                .andExpect(jsonPath("$.execution.toolCalls[0].name").value("createTask"))
                .andExpect(jsonPath("$.execution.llmCalls").value(4));

        assertThat(taskRepository.findAll()).singleElement().satisfies(task -> {
            assertThat(task.getTitle()).isEqualTo("Review the architecture document");
            assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
            assertThat(task.getDueDate()).isEqualTo(LocalDate.parse("2099-11-14"));
            // The owner is the authenticated caller; the model never supplied it.
            assertThat(task.getOwnerId()).isEqualTo(aliceId);
        });
    }

    @Test
    void supervisorRoutesToSummaryAgentWhichReadsRealStatistics() throws Exception {
        taskRepository.save(new Task(aliceId, "Overdue thing", null, TaskPriority.HIGH, LocalDate.parse("2020-01-01"), Instant.now()));
        taskRepository.save(new Task(aliceId, "Done thing", null, TaskPriority.LOW, null, Instant.now()));
        String conversation = newConversation();
        model.invokeAgent("SummaryAgent", "How am I doing?")
                .toolCall("getTaskStatistics", "{}")
                .text("You have 2 tasks, 1 overdue.")
                .done("Summarised.");

        mockMvc.perform(post("/api/ai/supervisor/chat").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"%s\",\"message\":\"How am I doing?\"}".formatted(conversation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You have 2 tasks, 1 overdue."))
                .andExpect(jsonPath("$.dataChanged").value(false))
                .andExpect(jsonPath("$.execution.agents[0].name").value("SummaryAgent"));

        String toolResult = ScriptedChatModel.lastToolResult(model.requests().get(2)).orElseThrow();
        assertThat(toolResult).contains("\"total\":2").contains("\"overdue\":1");
        assertThat(taskRepository.findAll()).allMatch(task -> task.getStatus() == TaskStatus.TODO);
    }

    @Test
    void rejectsBlankMessages() throws Exception {
        mockMvc.perform(post("/api/ai/supervisor/chat").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"%s\",\"message\":\"  \"}".formatted(newConversation())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").value("Message cannot be empty"));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void translatesModelFailuresIntoProblemDetails() throws Exception {
        String conversation = newConversation();
        model.failWith(new dev.langchain4j.exception.TimeoutException("read timed out"));

        mockMvc.perform(post("/api/ai/supervisor/chat").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"%s\",\"message\":\"Plan my day\"}".formatted(conversation)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.title").value("AI service error"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void listsTheAgentsBehindTheSupervisor() throws Exception {
        mockMvc.perform(get("/api/ai/agents").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].name").value("TaskAgent"))
                .andExpect(jsonPath("$[0].canModifyTasks").value(true))
                .andExpect(jsonPath("$[2].name").value("PlanningAgent"))
                .andExpect(jsonPath("$[2].canModifyTasks").value(false));
    }

    @Test
    void phaseOneEndpointStillWorksWithTheSameTools() throws Exception {
        String conversation = newConversation();
        model.toolCall("listTasks", "{\"statuses\":[\"TODO\"],\"page\":0,\"size\":10}")
                .text("You have no pending tasks.");

        mockMvc.perform(post("/api/ai/chat").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"%s\",\"message\":\"Show my pending tasks\"}".formatted(conversation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversation))
                .andExpect(jsonPath("$.message").value("You have no pending tasks."));
        assertThat(ScriptedChatModel.toolNames(model.requests().get(0)))
                .contains("createTask", "listTasks", "getTaskById", "updateTask", "updateTaskStatus", "getToday")
                // the supervisor's read-only tools must not leak into the Phase 1 agent
                .doesNotContain("findTasks", "getPlanningContext", "getTaskStatistics");
    }

    private String newConversation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/conversations").with(alice()))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.conversationId");
    }
}
