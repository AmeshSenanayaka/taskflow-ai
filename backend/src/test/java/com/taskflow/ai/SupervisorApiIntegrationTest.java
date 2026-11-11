package com.taskflow.ai;

import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over HTTP with the real Spring wiring, a real PostgreSQL and the scripted model:
 * the supervisor routes to the TaskAgent, whose tool writes through TaskService to the database.
 */
class SupervisorApiIntegrationTest extends AbstractAiIntegrationTest {

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
    void supervisorRoutesToTaskAgentWhichCreatesATaskInTheDatabase() throws Exception {
        model.invokeAgent("TaskAgent", "Create a high-priority task to review the architecture document on 2026-11-14")
                .toolCall("createTask", """
                        {"title":"Review the architecture document","priority":"HIGH","dueDate":"2026-11-14"}
                        """)
                .text("Created \"Review the architecture document\" (HIGH), due 2026-11-14.")
                .done("Created the task.");

        mockMvc.perform(post("/api/ai/supervisor/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"it-conv-1",
                                 "message":"Create a high-priority task to review the architecture document on 2026-11-14"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("it-conv-1"))
                .andExpect(jsonPath("$.message").value("Created \"Review the architecture document\" (HIGH), due 2026-11-14."))
                .andExpect(jsonPath("$.dataChanged").value(true))
                .andExpect(jsonPath("$.execution.requestId").isNotEmpty())
                .andExpect(jsonPath("$.execution.agents", hasSize(1)))
                .andExpect(jsonPath("$.execution.agents[0].name").value("TaskAgent"))
                .andExpect(jsonPath("$.execution.toolCalls[0].name").value("createTask"))
                .andExpect(jsonPath("$.execution.llmCalls").value(4));

        assertThat(taskRepository.findAll()).singleElement().satisfies(task -> {
            assertThat(task.getTitle()).isEqualTo("Review the architecture document");
            assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
            assertThat(task.getDueDate()).isEqualTo(LocalDate.parse("2026-11-14"));
        });
    }

    @Test
    void supervisorRoutesToSummaryAgentWhichReadsRealStatistics() throws Exception {
        taskRepository.save(new Task("Overdue thing", null, TaskPriority.HIGH, LocalDate.parse("2020-01-01"), Instant.now()));
        taskRepository.save(new Task("Done thing", null, TaskPriority.LOW, null, Instant.now()));
        model.invokeAgent("SummaryAgent", "How am I doing?")
                .toolCall("getTaskStatistics", "{}")
                .text("You have 2 tasks, 1 overdue.")
                .done("Summarised.");

        mockMvc.perform(post("/api/ai/supervisor/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"it-conv-2\",\"message\":\"How am I doing?\"}"))
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
        mockMvc.perform(post("/api/ai/supervisor/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"it-conv-3\",\"message\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").value("Message cannot be empty"));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void translatesModelFailuresIntoProblemDetails() throws Exception {
        model.failWith(new dev.langchain4j.exception.TimeoutException("read timed out"));

        mockMvc.perform(post("/api/ai/supervisor/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"it-conv-4\",\"message\":\"Plan my day\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.title").value("AI service error"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void listsTheAgentsBehindTheSupervisor() throws Exception {
        mockMvc.perform(get("/api/ai/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].name").value("TaskAgent"))
                .andExpect(jsonPath("$[0].canModifyTasks").value(true))
                .andExpect(jsonPath("$[2].name").value("PlanningAgent"))
                .andExpect(jsonPath("$[2].canModifyTasks").value(false));
    }

    @Test
    void phaseOneEndpointStillWorksWithTheSameTools() throws Exception {
        model.toolCall("listTasks", "{\"statuses\":[\"TODO\"],\"page\":0,\"size\":10}")
                .text("You have no pending tasks.");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"it-conv-5\",\"message\":\"Show my pending tasks\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You have no pending tasks."));
        assertThat(ScriptedChatModel.toolNames(model.requests().get(0)))
                .contains("createTask", "listTasks", "getTaskById", "updateTask", "updateTaskStatus", "getToday");
    }
}
