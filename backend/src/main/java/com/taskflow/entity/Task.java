package com.taskflow.entity;

import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * @param dueDate the calendar day the task is due, or null. It is a {@link LocalDate} on purpose: a deadline
     *                belongs to a day, not an instant, so it never shifts when the user's timezone changes.
     * @param now     the creation instant, supplied by the caller's {@link java.time.Clock} so tests are repeatable.
     */
    public Task(String title, String description, TaskPriority priority, LocalDate dueDate, Instant now) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = TaskStatus.TODO;
        this.dueDate = dueDate;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Records that the task was modified at {@code now}. Called by the service before every save. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    /**
     * Applies a status transition and keeps {@code completedAt} consistent with it.
     * The timestamp is only touched when the completion state actually changes.
     */
    public void changeStatus(TaskStatus newStatus, Instant now) {
        boolean wasCompleted = this.status == TaskStatus.COMPLETED;
        boolean willBeCompleted = newStatus == TaskStatus.COMPLETED;

        this.status = newStatus;

        if (willBeCompleted && !wasCompleted) {
            this.completedAt = now;
        } else if (!willBeCompleted) {
            this.completedAt = null;
        }
    }

}
