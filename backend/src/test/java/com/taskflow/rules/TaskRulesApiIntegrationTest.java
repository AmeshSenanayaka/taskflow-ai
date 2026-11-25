package com.taskflow.rules;

import com.jayway.jsonpath.JsonPath;
import com.taskflow.AbstractIntegrationTest;
import com.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static com.taskflow.support.TestUsers.alice;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The task rules over HTTP: a refused change is a 422 Problem Detail with rule codes and field errors. */
@AutoConfigureMockMvc
class TaskRulesApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void clean() {
        taskRepository.deleteAll();
    }

    @Test
    void aDueDateInThePastIsRefused() throws Exception {
        // alice's timezone is Asia/Colombo; two days back is in the past whatever the server's clock zone.
        String past = LocalDate.now(ZoneId.of("Asia/Colombo")).minusDays(2).toString();

        mockMvc.perform(post("/api/tasks").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Backdated\",\"dueDate\":\"" + past + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violation"))
                .andExpect(jsonPath("$.violations[0].rule").value("TASK_DUE_DATE_NOT_IN_PAST"))
                .andExpect(jsonPath("$.errors.dueDate").exists());
    }

    @Test
    void aSecondOpenTaskWithTheSameTitleIsRefused() throws Exception {
        create("Renew passport");

        mockMvc.perform(post("/api/tasks").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"renew PASSPORT\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].rule").value("TASK_UNIQUE_OPEN_TITLE"))
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void aCompletedTaskMustBeReopenedBeforeItsPriorityChanges() throws Exception {
        String id = create("Close the books");
        mockMvc.perform(patch("/api/tasks/{id}/status", id).with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/tasks/{id}", id).with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Close the books\",\"priority\":\"HIGH\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].rule").value("TASK_COMPLETED_IS_FROZEN"));

        // Reopening in the same request is allowed.
        mockMvc.perform(put("/api/tasks/{id}", id).with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Close the books\",\"priority\":\"HIGH\",\"status\":\"TODO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void anEstimateIsStoredAndValidated() throws Exception {
        mockMvc.perform(post("/api/tasks").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Estimated\",\"estimatedMinutes\":45}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedMinutes").value(45));
        mockMvc.perform(post("/api/tasks").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Too long\",\"estimatedMinutes\":2000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.estimatedMinutes").exists());
    }

    private String create(String title) throws Exception {
        String body = mockMvc.perform(post("/api/tasks").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
