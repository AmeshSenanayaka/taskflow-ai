package com.taskflow.ai.orchestration;

import com.taskflow.ai.ScriptedChatModel;
import com.taskflow.ai.agent.TaskFlowSupervisor;
import com.taskflow.ai.config.SupervisorProperties;
import com.taskflow.ai.conversation.ConversationRef;
import com.taskflow.ai.dto.AgentCallInfo;
import com.taskflow.ai.dto.SupervisorChatResponse;
import com.taskflow.ai.dto.ToolCallInfo;
import com.taskflow.ai.exception.AiServiceException;
import com.taskflow.ai.service.MultiAgentService;
import com.taskflow.ai.tool.PlanTools;
import com.taskflow.ai.tool.TaskQueryTools;
import com.taskflow.ai.tool.TaskTools;
import com.taskflow.dto.request.CreateTaskRequest;
import com.taskflow.dto.response.DashboardStatsResponse;
import com.taskflow.dto.response.PageResponse;
import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.DashboardService;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.TaskService;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the real LangChain4j supervisor loop with a scripted model: the planner's choices and the
 * sub-agents' answers are replayed in order, while the tools call mocked services. This proves routing,
 * tool wiring, memory, the current-date context, limits and error translation without an LLM provider.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskFlowSupervisorTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    @Mock
    private TaskService taskService;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private PreferencesService preferencesService;
    @Mock
    private CurrentUserProvider currentUser;

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID OTHER_USER = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

    private final ScriptedChatModel model = new ScriptedChatModel();
    private TaskFlowSupervisor supervisor;
    private MultiAgentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(currentUser.userId()).thenReturn(USER);
        when(preferencesService.getZoneId(USER)).thenReturn(ZONE);
        when(preferencesService.get(USER)).thenReturn(new PreferencesResponse(ZONE.getId(), 480, NOW));

        TaskTools taskTools = new TaskTools(taskService, preferencesService, currentUser,
                new com.taskflow.ai.guardrail.GuardrailProperties(true, 10, 1));
        TaskQueryTools queryTools = new TaskQueryTools(taskService, dashboardService, preferencesService, clock, currentUser);
        AgentContextProvider context = new AgentContextProvider(preferencesService, clock, currentUser);
        PlanTools planTools = new PlanTools(taskService, preferencesService, currentUser, List.of(
                new com.taskflow.rules.plan.PlanWellFormedRule(),
                new com.taskflow.rules.plan.PlanWithinAvailableMinutesRule(),
                new com.taskflow.rules.plan.PlanOnlyOwnOpenTasksRule(),
                new com.taskflow.rules.plan.PlanNoOverlapRule()));
        SupervisorProperties properties = new SupervisorProperties(
                3, 8, 20, SupervisorResponseStrategy.LAST, SupervisorContextStrategy.CHAT_MEMORY);
        TaskFlowAgentFactory factory = new TaskFlowAgentFactory(
                model, taskTools, queryTools, planTools,
                id -> MessageWindowChatMemory.builder().id(id).maxMessages(30).build(),
                context::render, properties, new AgentRunRecorder(),
                // No agent guardrail here (tested on its own); the planner's memory lives in a store, as in
                // production (PostgreSQL), because the in-process AgenticScope is evicted after every turn.
                null, 0, new InMemoryChatMemoryStore());
        supervisor = factory.supervisor();
        // Guardrails are tested on their own (ConversationGuardrailsTest); here they would query the mocked services.
        service = new MultiAgentService(supervisor, com.taskflow.ai.guardrail.ConversationGuardrails.disabled());
    }

    @Test
    void routesASummaryRequestToTheSummaryAgentAndReturnsItsAnswer() {
        when(dashboardService.getStats(USER)).thenReturn(new DashboardStatsResponse(20, 8, 5, 7, 2, 3));
        model.invokeAgent("SummaryAgent", "Summarise my progress")
                .toolCall("getTaskStatistics", "{}")
                .text("7 of 20 tasks completed, 2 overdue.")
                .done("Summarised the user's progress.");

        SupervisorChatResponse response = service.chat(conversation(USER, "conv-1"), "Summarise my progress");

        assertThat(response.message()).isEqualTo("7 of 20 tasks completed, 2 overdue.");
        assertThat(response.dataChanged()).isFalse();
        assertThat(response.execution().agents()).extracting(AgentCallInfo::name).containsExactly("SummaryAgent");
        assertThat(response.execution().toolCalls()).extracting(ToolCallInfo::name).containsExactly("getTaskStatistics");
        assertThat(response.execution().toolCalls()).allMatch(ToolCallInfo::success);
        assertThat(response.execution().llmCalls()).isEqualTo(4);
        assertThat(response.execution().tokenUsage().totalTokens()).isEqualTo(4 * 120);
        assertThat(model.remainingScript()).isZero();
        // Phase 3.4: the in-process AgenticScope is evicted after the turn; only chat memory carries over.
        assertThat(supervisor.getAgenticScope(conversation(USER, "conv-1").memoryKey())).isNull();

        List<ChatRequest> requests = model.requests();
        assertThat(requests).hasSize(4);
        // 1. the planner sees every sub-agent card and the routing policy
        String plannerPrompt = ScriptedChatModel.systemMessage(requests.get(0));
        assertThat(plannerPrompt).contains("TaskAgent", "PriorityAgent", "PlanningAgent", "SummaryAgent")
                .contains("Routing policy");
        assertThat(ScriptedChatModel.lastUserMessage(requests.get(0))).get().asString().contains("Summarise my progress");
        // 2. the SummaryAgent gets its own prompt, only read-only tools and today's date in the user's timezone
        String summaryPrompt = ScriptedChatModel.systemMessage(requests.get(1));
        assertThat(summaryPrompt).startsWith("You are SummaryAgent")
                .contains("Today: 2026-11-08 (Sunday)", "Timezone: Asia/Colombo", "Daily available minutes: 480");
        assertThat(ScriptedChatModel.toolNames(requests.get(1)))
                .containsExactlyInAnyOrder("getPlanningContext", "getTaskStatistics", "findTasks");
        // 3. the tool result is fed back to the agent
        assertThat(ScriptedChatModel.lastToolResult(requests.get(2))).get().asString().contains("\"overdue\":2");
    }

    @Test
    void routesATaskChangeToTheTaskAgentAndReportsDataChanged() {
        UUID id = UUID.randomUUID();
        TaskResponse created = task(id, "Review the architecture document", TaskPriority.HIGH, TaskStatus.TODO,
                LocalDate.parse("2026-11-09"));
        when(taskService.create(eq(USER), any(CreateTaskRequest.class))).thenReturn(created);
        model.invokeAgent("TaskAgent", "Create a high-priority task to review the architecture document tomorrow")
                .toolCall("createTask", """
                        {"title":"Review the architecture document","priority":"HIGH","dueDate":"2026-11-09"}
                        """)
                .text("Created \"Review the architecture document\" (HIGH), due 2026-11-09.")
                .done("Created the task.");

        SupervisorChatResponse response = service.chat(conversation(USER, "conv-2"),
                "Create a high-priority task to review the architecture document tomorrow");

        assertThat(response.message()).startsWith("Created \"Review the architecture document\"");
        assertThat(response.dataChanged()).isTrue();
        assertThat(response.execution().agents()).extracting(AgentCallInfo::name).containsExactly("TaskAgent");
        verify(taskService).create(USER, new CreateTaskRequest("Review the architecture document", null,
                TaskPriority.HIGH, LocalDate.parse("2026-11-09"), null));
        // the TaskAgent has the read/write tools and the Phase 1 prompt plus the date context
        ChatRequest taskAgentRequest = model.requests().get(1);
        assertThat(ScriptedChatModel.toolNames(taskAgentRequest))
                .contains("createTask", "listTasks", "getTaskById", "updateTask", "updateTaskStatus", "getToday");
        assertThat(ScriptedChatModel.systemMessage(taskAgentRequest))
                .contains("You are TaskAgent").contains("Today: 2026-11-08");
    }

    @Test
    void taskAgentKeepsConversationMemoryAcrossSupervisorTurns() {
        UUID id = UUID.randomUUID();
        TaskResponse article = task(id, "Write the article", TaskPriority.MEDIUM, TaskStatus.TODO, null);
        when(taskService.list(eq(USER), any(), any(), anyInt(), anyInt())).thenReturn(page(article));
        when(taskService.patch(eq(USER), eq(id), any())).thenReturn(
                task(id, "Write the article", TaskPriority.LOW, TaskStatus.TODO, null));
        model.invokeAgent("TaskAgent", "Find my article task")
                .toolCall("listTasks", "{\"search\":\"article\",\"page\":0,\"size\":10}")
                .text("Found \"Write the article\" (MEDIUM).")
                .done("Found the task.");
        service.chat(conversation(USER, "conv-3"), "Find my article task");

        model.invokeAgent("TaskAgent", "Change its priority to LOW")
                .toolCall("updateTask", "{\"taskId\":\"" + id + "\",\"priority\":\"LOW\"}")
                .text("Changed the priority of \"Write the article\" to LOW.")
                .done("Updated the priority.");
        SupervisorChatResponse second = service.chat(conversation(USER, "conv-3"), "Change its priority to LOW");

        assertThat(second.message()).contains("LOW");
        assertThat(second.dataChanged()).isTrue();
        // The second TaskAgent call still sees the first turn (user question, tool result, answer).
        ChatRequest secondTaskAgentRequest = model.requests().get(5);
        assertThat(secondTaskAgentRequest.messages().stream().map(Object::toString).toList())
                .anyMatch(text -> text.contains("Find my article task"))
                .anyMatch(text -> text.contains("Found \"Write the article\" (MEDIUM)."))
                .anyMatch(text -> text.contains("Change its priority to LOW"));
        // The supervisor's planner also remembers the previous turn.
        ChatRequest secondPlannerRequest = model.requests().get(4);
        assertThat(secondPlannerRequest.messages().size()).isGreaterThan(2);
    }

    @Test
    void aRequestForTwoAgentsReturnsBothAnswersInTheOrderTheyRan() {
        when(dashboardService.getStats(USER)).thenReturn(new DashboardStatsResponse(20, 8, 5, 7, 2, 3));
        when(taskService.list(eq(USER), any(), any(), anyInt(), anyInt())).thenReturn(page());
        model.invokeAgent("SummaryAgent", "Summarise my week")
                .toolCall("getTaskStatistics", "{}")
                .text("7 of 20 tasks completed, 2 overdue.")
                .invokeAgent("PlanningAgent", "Plan tomorrow")
                .toolCall("findTasks", "{\"statuses\":[\"TODO\",\"IN_PROGRESS\"]}")
                .text("Plan for 2026-11-09: no open tasks to schedule.")
                .done("Summarised the week and planned tomorrow.");

        SupervisorChatResponse response = service.chat(conversation(USER, "conv-9"), "Summarise my week and then plan tomorrow");

        // Neither answer is dropped and neither is re-told by the planner.
        assertThat(response.message()).isEqualTo(
                "7 of 20 tasks completed, 2 overdue.\n\nPlan for 2026-11-09: no open tasks to schedule.");
        assertThat(response.execution().agents()).extracting(AgentCallInfo::name)
                .containsExactly("SummaryAgent", "PlanningAgent");
        assertThat(response.dataChanged()).isFalse();
        assertThat(model.remainingScript()).isZero();
    }

    @Test
    void onlyTheToolsMarkedAsMutatingReportDataChanged() {
        assertThat(AgentRunRecorder.mutatingToolNames(TaskTools.class))
                .containsExactlyInAnyOrder("createTask", "updateTask", "updateTaskStatus");
        assertThat(AgentRunRecorder.mutatingToolNames(TaskQueryTools.class)).isEmpty();
    }

    @Test
    void analyticalAgentsCannotChangeTasks() {
        when(taskService.list(eq(USER), any(), any(), anyInt(), anyInt())).thenReturn(page());
        model.invokeAgent("PriorityAgent", "What should I focus on?")
                .toolCall("findTasks", "{\"statuses\":[\"TODO\",\"IN_PROGRESS\"]}")
                .text("You have no open tasks.")
                .done("Nothing to prioritise.");

        SupervisorChatResponse response = service.chat(conversation(USER, "conv-4"), "What should I focus on?");

        assertThat(response.message()).isEqualTo("You have no open tasks.");
        assertThat(ScriptedChatModel.toolNames(model.requests().get(1)))
                .doesNotContain("createTask", "updateTask", "updateTaskStatus");
        verify(taskService, never()).create(any(), any());
        verify(taskService, never()).update(any(), any(), any());
        verify(taskService, never()).patch(any(), any(), any());
    }

    @Test
    void plannerCanAnswerDirectlyWithoutCallingAnyAgent() {
        model.done("Hello! I can manage, prioritise, plan and summarise your tasks.");

        SupervisorChatResponse response = service.chat(conversation(USER, "conv-5"), "Hello");

        assertThat(response.message()).isEqualTo("Hello! I can manage, prioritise, plan and summarise your tasks.");
        assertThat(response.execution().agents()).isEmpty();
        assertThat(response.execution().llmCalls()).isEqualTo(1);
        assertThat(response.dataChanged()).isFalse();
    }

    @Test
    void toolErrorsAreExplainedToTheAgentNotThrownToTheUser() {
        when(dashboardService.getStats(USER)).thenThrow(new IllegalStateException("connection to db-host refused"));
        model.invokeAgent("SummaryAgent", "Summarise")
                .toolCall("getTaskStatistics", "{}")
                .text("I could not load the statistics right now.")
                .done("Statistics unavailable.");

        SupervisorChatResponse response = service.chat(conversation(USER, "conv-6"), "Summarise");

        assertThat(response.message()).isEqualTo("I could not load the statistics right now.");
        String toolResult = ScriptedChatModel.lastToolResult(model.requests().get(2)).orElseThrow();
        assertThat(toolResult).startsWith("Error:").contains("getTaskStatistics").doesNotContain("db-host");
        assertThat(response.execution().toolCalls()).extracting(ToolCallInfo::success).containsExactly(false);
    }

    @Test
    void stopsAfterTheConfiguredNumberOfAgentInvocations() {
        when(dashboardService.getStats(USER)).thenReturn(new DashboardStatsResponse(1, 1, 0, 0, 0, 0));
        for (int i = 0; i < 3; i++) {
            model.invokeAgent("SummaryAgent", "again").text("Summary " + i);
        }
        model.done("Finished");

        SupervisorChatResponse response = service.chat(conversation(USER, "conv-7"), "Loop forever");

        // max-agent-invocations is 3 in this test: the fourth planner call never happens.
        assertThat(response.execution().agents()).hasSize(3);
        assertThat(response.message()).isEqualTo("Summary 2");
        assertThat(model.remainingScript()).isEqualTo(1);
    }

    @Test
    void providerFailuresAreTranslatedIntoUserSafeErrors() {
        model.failWith(new RateLimitException("429 Too Many Requests: { \"error\": \"rate limited\", \"key\": \"sk-secret\" }"));

        assertThatThrownBy(() -> service.chat(conversation(USER, "conv-8"), "Summarise my progress"))
                .isInstanceOfSatisfying(AiServiceException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(error.retryable()).isTrue();
                    assertThat(error.getMessage()).doesNotContain("sk-secret");
                });
    }

    @Test
    void describesTheFourAgentsForTheUi() {
        assertThat(service.agents()).extracting("name")
                .containsExactly("TaskAgent", "PriorityAgent", "PlanningAgent", "SummaryAgent");
        assertThat(service.agents()).filteredOn("canModifyTasks", true).extracting("name").containsExactly("TaskAgent");
        assertThat(service.agents()).allSatisfy(agent -> assertThat(agent.description()).doesNotContain("\n"));
    }

    @Test
    void chatMemoryIsKeyedByOwnerSoTwoUsersNeverShareIt() {
        TaskResponse article = task(UUID.randomUUID(), "Write the article", TaskPriority.MEDIUM, TaskStatus.TODO, null);
        when(taskService.list(eq(USER), any(), any(), anyInt(), anyInt())).thenReturn(page(article));
        model.invokeAgent("TaskAgent", "Find my article task")
                .toolCall("listTasks", "{\"search\":\"article\",\"page\":0,\"size\":10}")
                .text("Found \"Write the article\" (MEDIUM).")
                .done("Found the task.");
        service.chat(conversation(USER, "shared-id"), "Find my article task");

        // Same conversation id, different owner (ownership is normally rejected earlier; this is the second line of defence).
        model.invokeAgent("TaskAgent", "What did I just ask?")
                .text("I don't have any earlier messages in this conversation.")
                .done("Answered.");
        service.chat(conversation(OTHER_USER, "shared-id"), "What did I just ask?");

        ChatRequest otherUsersTaskAgentRequest = model.requests().get(5);
        assertThat(otherUsersTaskAgentRequest.messages().stream().map(Object::toString).toList())
                .noneMatch(text -> text.contains("Find my article task"))
                .noneMatch(text -> text.contains("Write the article"));
    }

    /** A conversation as ConversationService would return it after the ownership check. */
    private static ConversationRef conversation(UUID owner, String name) {
        return new ConversationRef(owner, UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
    }

    private static TaskResponse task(UUID id, String title, TaskPriority priority, TaskStatus status, LocalDate dueDate) {
        return new TaskResponse(id, title, null, priority, status, dueDate, NOW, NOW, null, null);
    }

    private static PageResponse<TaskResponse> page(TaskResponse... tasks) {
        List<TaskResponse> content = List.of(tasks);
        return new PageResponse<>(content, 0, 20, content.size(), 1, true, true);
    }
}
