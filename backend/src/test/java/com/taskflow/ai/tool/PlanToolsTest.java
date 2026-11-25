package com.taskflow.ai.tool;

import com.taskflow.ai.exception.ToolExecutionException;
import com.taskflow.ai.orchestration.AgentRunRecorder;
import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.exception.TaskNotFoundException;
import com.taskflow.rules.plan.PlanNoOverlapRule;
import com.taskflow.rules.plan.PlanOnlyOwnOpenTasksRule;
import com.taskflow.rules.plan.PlanWellFormedRule;
import com.taskflow.rules.plan.PlanWithinAvailableMinutesRule;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** The plan rules as the PlanningAgent meets them through submitPlan. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanToolsTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final Instant NOW = Instant.parse("2026-11-09T03:30:00Z");
    private static final UUID LOGIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REVIEW = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DONE = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID FOREIGN = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock
    private TaskService taskService;
    @Mock
    private PreferencesService preferencesService;
    @Mock
    private CurrentUserProvider currentUser;

    private PlanTools tools;
    private AgentRunRecorder.Run run;

    @BeforeEach
    void setUp() {
        when(currentUser.userId()).thenReturn(USER);
        when(preferencesService.today(USER)).thenReturn(TODAY);
        when(preferencesService.get(USER)).thenReturn(new PreferencesResponse("Asia/Colombo", 180, NOW));
        when(taskService.getById(USER, LOGIN)).thenReturn(task(LOGIN, "Fix login bug", TaskStatus.TODO));
        when(taskService.getById(USER, REVIEW)).thenReturn(task(REVIEW, "Prepare sprint review", TaskStatus.IN_PROGRESS));
        when(taskService.getById(USER, DONE)).thenReturn(task(DONE, "Pay bill", TaskStatus.COMPLETED));
        when(taskService.getById(USER, FOREIGN)).thenThrow(new TaskNotFoundException(FOREIGN));
        tools = new PlanTools(taskService, preferencesService, currentUser, List.of(new PlanWellFormedRule(),
                new PlanWithinAvailableMinutesRule(), new PlanOnlyOwnOpenTasksRule(), new PlanNoOverlapRule()));
        run = AgentRunRecorder.open();
    }

    @AfterEach
    void tearDown() {
        AgentRunRecorder.close();
    }

    @Test
    void aValidPlanIsAcceptedAndRecordedForTheResponse() {
        PlanTools.PlanSubmission result = tools.submitPlan("2026-11-09", List.of(
                block(REVIEW, "10:45", 60), block(LOGIN, "09:00", 90)));

        assertThat(result.accepted()).isTrue();
        assertThat(result.plan().totalMinutes()).isEqualTo(150);
        assertThat(result.plan().entries()).extracting("title").containsExactly("Fix login bug", "Prepare sprint review");
        assertThat(result.plan().entries().get(0).end()).isEqualTo("10:30");
        assertThat(run.plan()).isEqualTo(result.plan());
    }

    @Test
    void aPlanLongerThanTheAvailableMinutesIsRejectedWithTheReason() {
        PlanTools.PlanSubmission result = tools.submitPlan("2026-11-09", List.of(
                block(LOGIN, "09:00", 120), block(REVIEW, "11:00", 120)));

        assertThat(result.accepted()).isFalse();
        assertThat(result.problems()).anyMatch(problem -> problem.contains("240 minutes") && problem.contains("180"));
        assertThat(run.plan()).isNull();
    }

    @Test
    void completedForeignAndDuplicateTasksAreRejected() {
        PlanTools.PlanSubmission result = tools.submitPlan("2026-11-09", List.of(
                block(DONE, "09:00", 30), block(FOREIGN, "09:30", 30), block(LOGIN, "10:00", 30), block(LOGIN, "11:00", 30)));

        assertThat(result.accepted()).isFalse();
        assertThat(result.problems())
                .anyMatch(problem -> problem.contains("already completed"))
                .anyMatch(problem -> problem.contains(FOREIGN.toString()) && problem.contains("not found"))
                .anyMatch(problem -> problem.contains("more than once"));
    }

    @Test
    void overlappingAndMalformedBlocksAreRejected() {
        PlanTools.PlanSubmission result = tools.submitPlan("2026-11-09", List.of(
                block(LOGIN, "09:00", 60), block(REVIEW, "09:30", 30),
                new PlanTools.PlanBlock("not-an-id", "9am", 30)));

        assertThat(result.accepted()).isFalse();
        assertThat(result.problems())
                .anyMatch(problem -> problem.contains("starts before"))
                .anyMatch(problem -> problem.contains("needs a valid taskId"));
    }

    @Test
    void afterThreeRejectionsTheAgentMustStopAndTellTheUser() {
        List<PlanTools.PlanBlock> tooLong = List.of(block(LOGIN, "09:00", 300));
        tools.submitPlan("2026-11-09", tooLong);
        tools.submitPlan("2026-11-09", tooLong);

        assertThatThrownBy(() -> tools.submitPlan("2026-11-09", tooLong))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("rejected 3 times");
    }

    @Test
    void aPlanForAPastDayIsRejectedAndABlankDateMeansToday() {
        assertThat(tools.submitPlan("2026-11-01", List.of(block(LOGIN, "09:00", 30))).accepted()).isFalse();
        assertThat(tools.submitPlan("", List.of(block(LOGIN, "09:00", 30))).plan().date()).isEqualTo(TODAY);
    }

    private static PlanTools.PlanBlock block(UUID taskId, String start, int minutes) {
        return new PlanTools.PlanBlock(taskId.toString(), start, minutes);
    }

    private static TaskResponse task(UUID id, String title, TaskStatus status) {
        return new TaskResponse(id, title, null, TaskPriority.HIGH, status, TODAY, NOW, NOW, null, null);
    }
}
