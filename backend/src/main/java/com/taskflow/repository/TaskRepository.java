package com.taskflow.repository;

import com.taskflow.entity.Task;
import com.taskflow.enums.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    long countByStatus(TaskStatus status);

    /** Loads a task with a row lock ({@code SELECT ... FOR UPDATE}) for read-modify-write updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.status <> com.taskflow.enums.TaskStatus.COMPLETED
              AND t.dueDate < :today
            """)
    long countOverdue(@Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.status <> com.taskflow.enums.TaskStatus.COMPLETED
              AND t.dueDate = :today
            """)
    long countDueToday(@Param("today") LocalDate today);
}
