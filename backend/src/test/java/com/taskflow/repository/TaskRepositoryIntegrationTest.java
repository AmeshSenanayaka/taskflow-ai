package com.taskflow.repository;

import com.taskflow.AbstractIntegrationTest;
import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.service.UserAccountService;
import com.taskflow.support.TestUsers;
import com.taskflow.specification.TaskFilter;
import com.taskflow.specification.TaskSort;
import com.taskflow.specification.TaskSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final Instant CREATED = Instant.parse("2026-10-30T08:00:00Z");

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountService userAccounts;

    private UUID owner;
    private UUID otherUser;

    @BeforeEach
    void seed() {
        owner = userAccounts.resolve(TestUsers.ALICE_SUBJECT, "alice").id();
        otherUser = userAccounts.resolve(TestUsers.BOB_SUBJECT, "bob").id();
        taskRepository.deleteAll();
        // Another user's tasks are in the same table and must never show up in the owner's results below.
        taskRepository.saveAll(List.of(
                new Task(otherUser, "Bob: Phase 0 notes", "bob's backlog", TaskPriority.HIGH, TODAY, CREATED),
                new Task(otherUser, "Bob: overdue", null, TaskPriority.LOW, TODAY.minusDays(9), CREATED)
        ));
        taskRepository.saveAll(List.of(
                task("Write Phase 0 article", "Medium series", TaskPriority.HIGH, TODAY.minusDays(2)),
                task("Review pull request", null, TaskPriority.MEDIUM, TODAY),
                task("Plan sprint", "Prepare the backlog", TaskPriority.LOW, TODAY.plusDays(3)),
                task("Buy groceries", "milk, eggs", TaskPriority.LOW, null),
                completed("Set up repository", TaskPriority.HIGH, TODAY.minusDays(5))
        ));
    }

    @Test
    void flywayMigrationsWereApplied() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3");

        // V3 hands the pre-Phase-3 preferences row to the demo user alice.
        Integer alicePreferences = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences WHERE user_id = ?::uuid", Integer.class, TestUsers.ALICE_SUBJECT);
        assertThat(alicePreferences).isEqualTo(1);
    }

    @Test
    void everyQueryIsScopedToTheOwner() {
        assertThat(find(TaskFilter.none(), TaskSort.defaultSort(), 0, 50).getContent())
                .extracting(Task::getOwnerId).containsOnly(owner);
        assertThat(taskRepository.countByOwnerId(owner)).isEqualTo(5);
        assertThat(taskRepository.countByOwnerId(otherUser)).isEqualTo(2);

        Task bobsTask = taskRepository.findAll().stream()
                .filter(task -> task.getOwnerId().equals(otherUser)).findFirst().orElseThrow();
        assertThat(taskRepository.findByIdAndOwnerId(bobsTask.getId(), owner)).isEmpty();
        assertThat(taskRepository.findByIdAndOwnerId(bobsTask.getId(), otherUser)).isPresent();
    }

    @Test
    void filtersByStatusList() {
        Page<Task> page = find(new TaskFilter(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                null, null, null, null, null), TaskSort.defaultSort(), 0, 10);

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).noneMatch(t -> t.getStatus() == TaskStatus.COMPLETED);
    }

    @Test
    void filtersByPriority() {
        Page<Task> page = find(new TaskFilter(null, TaskPriority.LOW, null, null, null, null),
                TaskSort.defaultSort(), 0, 10);

        assertThat(page.getContent()).extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Plan sprint", "Buy groceries");
    }

    @Test
    void filtersByExactDueDateAndRange() {
        Page<Task> exact = find(new TaskFilter(null, null, TODAY, null, null, null), TaskSort.defaultSort(), 0, 10);
        assertThat(exact.getContent()).extracting(Task::getTitle).containsExactly("Review pull request");

        Page<Task> upcoming = find(new TaskFilter(null, null, null, TODAY.plusDays(1), null, null),
                TaskSort.defaultSort(), 0, 10);
        assertThat(upcoming.getContent()).extracting(Task::getTitle).containsExactly("Plan sprint");

        Page<Task> past = find(new TaskFilter(null, null, null, null, TODAY.minusDays(1), null),
                TaskSort.defaultSort(), 0, 10);
        assertThat(past.getContent()).extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Write Phase 0 article", "Set up repository");
    }

    @Test
    void searchMatchesTitleOrDescriptionCaseInsensitively() {
        Page<Task> byTitle = find(new TaskFilter(null, null, null, null, null, "PHASE"), TaskSort.defaultSort(), 0, 10);
        assertThat(byTitle.getContent()).extracting(Task::getTitle).containsExactly("Write Phase 0 article");

        Page<Task> byDescription = find(new TaskFilter(null, null, null, null, null, "backlog"), TaskSort.defaultSort(), 0, 10);
        assertThat(byDescription.getContent()).extracting(Task::getTitle).containsExactly("Plan sprint");
    }

    @Test
    void sortsByPriorityRankNotAlphabetically() {
        Page<Task> page = find(TaskFilter.none(), new TaskSort(TaskSort.Field.PRIORITY, TaskSort.Direction.DESC), 0, 10);

        assertThat(page.getContent()).extracting(Task::getPriority)
                .containsExactly(TaskPriority.HIGH, TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW, TaskPriority.LOW);
    }

    @Test
    void sortsByDueDateWithNullsLast() {
        Page<Task> page = find(TaskFilter.none(), new TaskSort(TaskSort.Field.DUE_DATE, TaskSort.Direction.ASC), 0, 10);

        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly(
                "Set up repository", "Write Phase 0 article", "Review pull request", "Plan sprint", "Buy groceries");
    }

    @Test
    void paginatesAtDatabaseLevel() {
        TaskSort sort = new TaskSort(TaskSort.Field.DUE_DATE, TaskSort.Direction.ASC);

        Page<Task> first = find(TaskFilter.none(), sort, 0, 2);
        Page<Task> second = find(TaskFilter.none(), sort, 1, 2);
        Page<Task> third = find(TaskFilter.none(), sort, 2, 2);

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).hasSize(2);
        assertThat(third.getContent()).hasSize(1);
        assertThat(third.isLast()).isTrue();
    }

    @Test
    void dashboardCountQueriesRespectStatusAndDate() {
        assertThat(taskRepository.countOverdue(owner, TODAY)).isEqualTo(1); // completed overdue task is excluded
        assertThat(taskRepository.countDueToday(owner, TODAY)).isEqualTo(1);
        assertThat(taskRepository.countByOwnerIdAndStatus(owner, TaskStatus.COMPLETED)).isEqualTo(1);
        // bob's overdue and due-today tasks are not counted for alice
        assertThat(taskRepository.countOverdue(otherUser, TODAY)).isEqualTo(1);
    }

    private Page<Task> find(TaskFilter filter, TaskSort sort, int page, int size) {
        Specification<Task> spec = TaskSpecifications.ownedBy(owner)
                .and(TaskSpecifications.withFilter(filter))
                .and(TaskSpecifications.sortedBy(sort));
        return taskRepository.findAll(spec, PageRequest.of(page, size));
    }

    private Task task(String title, String description, TaskPriority priority, LocalDate dueDate) {
        return new Task(owner, title, description, priority, dueDate, CREATED);
    }

    private Task completed(String title, TaskPriority priority, LocalDate dueDate) {
        Task task = new Task(owner, title, null, priority, dueDate, CREATED);
        task.changeStatus(TaskStatus.COMPLETED, Instant.parse("2026-11-01T10:00:00Z"));
        return task;
    }
}
