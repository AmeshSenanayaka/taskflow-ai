package com.taskflow.security;

import com.jayway.jsonpath.JsonPath;
import com.taskflow.ai.AbstractAiIntegrationTest;
import com.taskflow.ai.ScriptedChatModel;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.taskflow.support.TestUsers.alice;
import static com.taskflow.support.TestUsers.bob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3.0 acceptance tests: two real users (alice, bob) against the full application, a real PostgreSQL
 * and the scripted model. Each test is one isolation guarantee the later Phase 3 milestones build on.
 */
class IdentityIsolationIntegrationTest extends AbstractAiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptedChatModel model;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void reset() {
        model.reset();
        taskRepository.deleteAll();
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/supervisor/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"x\",\"message\":\"Plan my day\"}"))
                .andExpect(status().isUnauthorized());
        // The API documentation stays public so the Authorize button can be used.
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void aUserCannotReadOrChangeAnotherUsersTask() throws Exception {
        String aliceTask = createTask(alice(), "Alice's private task");

        mockMvc.perform(get("/api/tasks").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // Another user's task looks exactly like a task that does not exist: 404, not 403.
        mockMvc.perform(get("/api/tasks/{id}", aliceTask).with(bob())).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tasks/{id}", aliceTask).with(bob())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\",\"priority\":\"LOW\",\"status\":\"TODO\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tasks/{id}/status", aliceTask).with(bob())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/{id}", aliceTask).with(bob())).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tasks/{id}", aliceTask).with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alice's private task"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void dashboardAndPreferencesArePerUser() throws Exception {
        createTask(alice(), "Counted for alice only");
        String alicePreferencesBefore = mockMvc.perform(get("/api/preferences").with(alice()))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/dashboard/stats").with(alice())).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/dashboard/stats").with(bob())).andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(put("/api/preferences").with(bob())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Europe/Berlin\",\"dailyAvailableMinutes\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"));

        String alicePreferencesAfter = mockMvc.perform(get("/api/preferences").with(alice()))
                .andReturn().getResponse().getContentAsString();
        assertThat(alicePreferencesAfter).isEqualTo(alicePreferencesBefore);
    }

    @Test
    void aConversationCanOnlyBeUsedByTheUserWhoStartedIt() throws Exception {
        String aliceConversation = newConversation(alice());

        chat(bob(), aliceConversation, "What did alice ask you?")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Conversation not found"));
        // Ids the server never issued are rejected the same way, whatever their shape.
        chat(alice(), UUID.randomUUID().toString(), "Hello").andExpect(status().isNotFound());
        chat(alice(), "my-own-made-up-id", "Hello").andExpect(status().isNotFound());
        mockMvc.perform(post("/api/ai/chat").with(bob())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"%s\",\"message\":\"Hi\"}".formatted(aliceConversation)))
                .andExpect(status().isNotFound());

        // Nothing above reached the model.
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void aiToolsCannotReachAnotherUsersTaskEvenWithItsId() throws Exception {
        String aliceTask = createTask(alice(), "Alice's private task");
        String bobConversation = newConversation(bob());
        // Bob's assistant is told (for example by a prompt injection) to complete alice's task by id.
        model.invokeAgent("TaskAgent", "Complete task " + aliceTask)
                .toolCall("updateTaskStatus", "{\"taskId\":\"" + aliceTask + "\",\"status\":\"COMPLETED\"}")
                .text("I could not find that task.")
                .done("Task not found.");

        chat(bob(), bobConversation, "Complete task " + aliceTask)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataChanged").value(false));

        String toolResult = ScriptedChatModel.lastToolResult(model.requests().get(2)).orElseThrow();
        assertThat(toolResult).contains("Task not found");
        assertThat(taskRepository.findById(UUID.fromString(aliceTask))).get()
                .satisfies(task -> assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO));
    }

    private String createTask(RequestPostProcessor user, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks").with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String newConversation(RequestPostProcessor user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/conversations").with(user))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.conversationId");
    }

    private org.springframework.test.web.servlet.ResultActions chat(RequestPostProcessor user, String conversationId,
                                                                     String message) throws Exception {
        return mockMvc.perform(post("/api/ai/supervisor/chat").with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"%s\",\"message\":\"%s\"}".formatted(conversationId, message)));
    }
}
