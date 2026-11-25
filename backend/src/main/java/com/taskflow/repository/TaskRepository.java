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

/**
 * Task persistence. Since Phase 3.0 every method the application uses takes the owner's id: a task that
 * belongs to someone else is simply "not found". The unscoped methods inherited from {@link JpaRepository}
 * ({@code findById}, {@code findAll}, {@code count}) remain for tests and must not be used by services.
 */
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    long countByOwnerIdAndStatus(UUID ownerId, TaskStatus status);

    /** Phase 3.2: does the owner have a task not in {@code status} with this title, ignoring case? */
    boolean existsByOwnerIdAndStatusNotAndTitleIgnoreCase(UUID ownerId, TaskStatus status, String title);

    /** Same, ignoring the task with id {@code excludedId} (the one being changed). */
    boolean existsByOwnerIdAndStatusNotAndTitleIgnoreCaseAndIdNot(UUID ownerId, TaskStatus status, String title,
                                                                 UUID excludedId);

    /** Loads one of the owner's tasks with a row lock ({@code SELECT ... FOR UPDATE}) for read-modify-write updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id AND t.ownerId = :ownerId")
    Optional<Task> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.ownerId = :ownerId
              AND t.status <> com.taskflow.enums.TaskStatus.COMPLETED
              AND t.dueDate < :today
            """)
    long countOverdue(@Param("ownerId") UUID ownerId, @Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.ownerId = :ownerId
              AND t.status <> com.taskflow.enums.TaskStatus.COMPLETED
              AND t.dueDate = :today
            """)
    long countDueToday(@Param("ownerId") UUID ownerId, @Param("today") LocalDate today);
}
