package com.taskflow.repository;

import com.taskflow.AbstractIntegrationTest;
import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

class TaskRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        taskRepository.deleteAll();
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
        assertThat(versions).containsExactly("1", "2");

        Integer preferenceRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_preferences", Integer.class);
        assertThat(preferenceRows).isEqualTo(1);
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
        assertThat(taskRepository.countOverdue(TODAY)).isEqualTo(1); // completed overdue task is excluded
        assertThat(taskRepository.countDueToday(TODAY)).isEqualTo(1);
        assertThat(taskRepository.countByStatus(TaskStatus.COMPLETED)).isEqualTo(1);
    }

    private Page<Task> find(TaskFilter filter, TaskSort sort, int page, int size) {
        Specification<Task> spec = TaskSpecifications.withFilter(filter).and(TaskSpecifications.sortedBy(sort));
        return taskRepository.findAll(spec, PageRequest.of(page, size));
    }

    private static Task task(String title, String description, TaskPriority priority, LocalDate dueDate) {
        return new Task(title, description, priority, dueDate);
    }

    private static Task completed(String title, TaskPriority priority, LocalDate dueDate) {
        Task task = new Task(title, null, priority, dueDate);
        task.changeStatus(TaskStatus.COMPLETED, Instant.parse("2026-11-01T10:00:00Z"));
        return task;
    }
}
