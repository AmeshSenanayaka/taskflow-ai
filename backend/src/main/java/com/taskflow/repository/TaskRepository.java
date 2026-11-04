package com.taskflow.repository;

import com.taskflow.entity.Task;
import com.taskflow.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    long countByStatus(TaskStatus status);

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
