package com.taskflow.rules;

import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import com.taskflow.rules.task.CompletedTaskIsFrozenRule;
import com.taskflow.rules.task.DueDateNotInPastRule;
import com.taskflow.rules.task.TaskChange;
import com.taskflow.rules.task.TaskChange.State;
import com.taskflow.rules.task.UniqueOpenTitleRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Phase 3.2 task rules, one behaviour per test. */
@ExtendWith(MockitoExtension.class)
class TaskRulesTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID TASK = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Mock
    private TaskRepository tasks;

    private final CompletedTaskIsFrozenRule frozen = new CompletedTaskIsFrozenRule();
    private final DueDateNotInPastRule notInPast = new DueDateNotInPastRule();

    private static State state(String title, TaskPriority priority, TaskStatus status, LocalDate due) {
        return new State(title, priority, status, due);
    }

    private static TaskChange update(State before, State after) {
        return new TaskChange(OWNER, TASK, before, after, TODAY);
    }

    private static TaskChange create(State after) {
        return new TaskChange(OWNER, null, null, after, TODAY);
    }

    // ---- completed tasks are frozen

    @Test
    void aCompletedTaskCannotChangePriorityOrDueDate() {
        State done = state("Report", TaskPriority.LOW, TaskStatus.COMPLETED, TODAY);

        assertThat(frozen.evaluate(update(done, state("Report", TaskPriority.HIGH, TaskStatus.COMPLETED, TODAY)))
                .violations()).singleElement().satisfies(v -> {
                    assertThat(v.rule()).isEqualTo(CompletedTaskIsFrozenRule.NAME);
                    assertThat(v.field()).isEqualTo("priority");
                });
        assertThat(frozen.evaluate(update(done, state("Report", TaskPriority.LOW, TaskStatus.COMPLETED, TODAY.plusDays(1))))
                .passed()).isFalse();
    }

    @Test
    void reopeningWhileChangingIsAllowedAndOtherFieldsStayEditable() {
        State done = state("Report", TaskPriority.LOW, TaskStatus.COMPLETED, TODAY);

        assertThat(frozen.evaluate(update(done, state("Report", TaskPriority.HIGH, TaskStatus.TODO, TODAY))).passed()).isTrue();
        assertThat(frozen.evaluate(update(done, state("Final report", TaskPriority.LOW, TaskStatus.COMPLETED, TODAY)))
                .passed()).isTrue();
    }

    // ---- due dates are not moved into the past

    @Test
    void aNewDueDateBeforeTodayIsRefused() {
        assertThat(notInPast.evaluate(create(state("A", TaskPriority.LOW, TaskStatus.TODO, TODAY.minusDays(1))))
                .violations()).singleElement().satisfies(v -> {
                    assertThat(v.field()).isEqualTo("dueDate");
                    assertThat(v.message()).contains("2026-11-08").contains("today is 2026-11-09");
                });
        State current = state("A", TaskPriority.LOW, TaskStatus.TODO, TODAY.plusDays(3));
        assertThat(notInPast.evaluate(update(current, state("A", TaskPriority.LOW, TaskStatus.TODO, TODAY.minusDays(3))))
                .passed()).isFalse();
    }

    @Test
    void todayAndExistingOverdueDatesAreAllowed() {
        assertThat(notInPast.evaluate(create(state("A", TaskPriority.LOW, TaskStatus.TODO, TODAY))).passed()).isTrue();
        // An overdue task keeps its date while other fields change.
        State overdue = state("A", TaskPriority.LOW, TaskStatus.TODO, TODAY.minusDays(5));
        assertThat(notInPast.evaluate(update(overdue, state("A", TaskPriority.HIGH, TaskStatus.IN_PROGRESS, TODAY.minusDays(5))))
                .passed()).isTrue();
    }

    // ---- no duplicate open titles

    @Test
    void creatingASecondOpenTaskWithTheSameTitleIsRefused() {
        UniqueOpenTitleRule unique = new UniqueOpenTitleRule(tasks);
        when(tasks.existsByOwnerIdAndStatusNotAndTitleIgnoreCase(OWNER, TaskStatus.COMPLETED, "Renew passport"))
                .thenReturn(true);

        assertThat(unique.evaluate(create(state("  Renew passport ", TaskPriority.LOW, TaskStatus.TODO, null)))
                .violations()).singleElement().satisfies(v -> assertThat(v.field()).isEqualTo("title"));
    }

    @Test
    void renamingAndReopeningAreCheckedAgainstOtherTasksOnly() {
        UniqueOpenTitleRule unique = new UniqueOpenTitleRule(tasks);
        when(tasks.existsByOwnerIdAndStatusNotAndTitleIgnoreCaseAndIdNot(OWNER, TaskStatus.COMPLETED, "Pay rent", TASK))
                .thenReturn(true);
        State done = state("Pay rent", TaskPriority.LOW, TaskStatus.COMPLETED, null);

        assertThat(unique.evaluate(update(done, state("Pay rent", TaskPriority.LOW, TaskStatus.TODO, null))).passed())
                .isFalse();
    }

    @Test
    void editsThatKeepTitleAndOpenStatusDoNotQueryAndCompletedTasksNeverClash() {
        UniqueOpenTitleRule unique = new UniqueOpenTitleRule(tasks);
        State open = state("Pay rent", TaskPriority.LOW, TaskStatus.TODO, null);

        assertThat(unique.evaluate(update(open, state("PAY RENT", TaskPriority.HIGH, TaskStatus.IN_PROGRESS, null))).passed())
                .isTrue();
        assertThat(unique.evaluate(create(state("Pay rent", TaskPriority.LOW, TaskStatus.COMPLETED, null))).passed()).isTrue();
        verifyNoInteractions(tasks);
    }

    // ---- engine

    @Test
    void theEngineReportsEveryViolationAtOnce() {
        TaskChange change = update(state("Report", TaskPriority.LOW, TaskStatus.COMPLETED, TODAY),
                state("Report", TaskPriority.HIGH, TaskStatus.COMPLETED, TODAY.minusDays(2)));

        assertThatThrownBy(() -> RuleEngine.enforce(List.of(frozen, notInPast), change))
                .isInstanceOfSatisfying(RuleViolationException.class, ex -> assertThat(ex.violations())
                        .extracting(RuleViolation::rule)
                        .containsExactly(CompletedTaskIsFrozenRule.NAME, DueDateNotInPastRule.NAME));
    }
}
