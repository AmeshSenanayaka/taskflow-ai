package com.taskflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.AbstractIntegrationTest;
import com.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
    }

    @Test
    void createReturns201AndPersistsTask() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Write article", "description": "Phase 0", "dueDate": "2026-11-15"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Write article"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-15"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        assertThat(taskRepository.findById(id)).isPresent();
    }

    @Test
    void createWithBlankTitleReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors.title").value("Title must not be blank"));
    }

    @Test
    void createWithTooLongTitleReturns400() throws Exception {
        String longTitle = "x".repeat(151);
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Title must be at most 150 characters"));
    }

    @Test
    void createWithInvalidPriorityEnumReturns400() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Task\", \"priority\": \"URGENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("URGENT")))
                .andExpect(jsonPath("$.detail").value(containsString("LOW, MEDIUM, HIGH")));
    }

    @Test
    void listWithInvalidStatusQueryParamReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("DONE")));
    }

    @Test
    void listWithInvalidPageSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownTaskReturns404ProblemDetail() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(get("/api/tasks/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Task not found"))
                .andExpect(jsonPath("$.detail").value(containsString(unknown.toString())));
    }

    @Test
    void updateReplacesFieldsAndReturns200() throws Exception {
        UUID id = createTask("Original", "LOW");

        mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated", "description": "New text", "priority": "HIGH",
                                 "status": "IN_PROGRESS", "dueDate": "2026-12-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.description").value("New text"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-01"));
    }

    @Test
    void updateWithMissingPriorityReturns400() throws Exception {
        UUID id = createTask("Original", "LOW");

        mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Updated\", \"status\": \"TODO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.priority").value("Priority is required"));
    }

    @Test
    void completeThenReopenManagesCompletedAt() throws Exception {
        UUID id = createTask("Finish me", "MEDIUM");

        mockMvc.perform(patch("/api/tasks/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(patch("/api/tasks/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"TODO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        assertThat(taskRepository.findById(id)).get()
                .satisfies(task -> assertThat(task.getCompletedAt()).isNull());
    }

    @Test
    void patchStatusWithInvalidValueReturns400() throws Exception {
        UUID id = createTask("Task", "MEDIUM");

        mockMvc.perform(patch("/api/tasks/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"DONE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204ThenTaskIsGone() throws Exception {
        UUID id = createTask("Delete me", "MEDIUM");

        mockMvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void listSupportsFilteringSortingAndPagination() throws Exception {
        createTask("Alpha", "LOW");
        createTask("Beta", "HIGH");
        createTask("Gamma", "MEDIUM");

        mockMvc.perform(get("/api/tasks")
                        .param("sortBy", "PRIORITY")
                        .param("sortDirection", "DESC")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("Beta"))
                .andExpect(jsonPath("$.content[1].title").value("Gamma"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/tasks").param("priority", "HIGH").param("search", "bet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Beta"));
    }

    @Test
    void dashboardStatsReflectPersistedTasks() throws Exception {
        UUID done = createTask("Done", "LOW");
        createTask("Open", "LOW");
        mockMvc.perform(patch("/api/tasks/{id}/status", done)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"COMPLETED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.inProgress").value(0))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.overdue").value(0))
                .andExpect(jsonPath("$.dueToday").value(0));
    }

    @Test
    void preferencesCanBeReadAndUpdated() throws Exception {
        mockMvc.perform(get("/api/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Colombo"))
                .andExpect(jsonPath("$.dailyAvailableMinutes").value(480));

        mockMvc.perform(put("/api/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\": \"Europe/Berlin\", \"dailyAvailableMinutes\": 360}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.dailyAvailableMinutes").value(360));

        mockMvc.perform(get("/api/preferences"))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"));

        // restore the default for other tests
        mockMvc.perform(put("/api/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\": \"Asia/Colombo\", \"dailyAvailableMinutes\": 480}"))
                .andExpect(status().isOk());
    }

    @Test
    void changingTheTimezoneDoesNotMoveAnExistingDueDate() throws Exception {
        // A due date is a calendar day. Moving the user from UTC+5:30 to UTC-10 (15.5 hours apart, so any
        // instant-based storage would shift the day) must leave the stored and returned date untouched.
        MvcResult created = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Submit report\", \"dueDate\": \"2026-11-15\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        try {
            mockMvc.perform(put("/api/preferences")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"timezone\": \"Pacific/Honolulu\", \"dailyAvailableMinutes\": 480}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/tasks/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dueDate").value("2026-11-15"));

            mockMvc.perform(get("/api/tasks").param("dueDate", "2026-11-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(id.toString()));
        } finally {
            // restore the default for other tests
            mockMvc.perform(put("/api/preferences")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"timezone\": \"Asia/Colombo\", \"dailyAvailableMinutes\": 480}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void preferencesRejectInvalidTimezone() throws Exception {
        mockMvc.perform(put("/api/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\": \"Mars/Olympus\", \"dailyAvailableMinutes\": 480}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.timezone").value(containsString("IANA")));
    }

    @Test
    void openApiDocumentIsAvailable() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(doc.get("info").get("title").asText()).isEqualTo("TaskFlow AI API");
        assertThat(doc.get("paths").has("/api/tasks")).isTrue();
        assertThat(doc.get("paths").has("/api/tasks/{id}/status")).isTrue();
        assertThat(doc.get("paths").has("/api/dashboard/stats")).isTrue();
        assertThat(doc.get("paths").has("/api/preferences")).isTrue();
    }

    private UUID createTask(String title, String priority) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\", \"priority\": \"" + priority + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
