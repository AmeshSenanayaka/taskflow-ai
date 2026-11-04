package com.taskflow.specification;

import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the database-level WHERE and ORDER BY clauses for task listing.
 * Filtering and sorting always happen in PostgreSQL, never in application memory.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> withFilter(TaskFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.dueDate() != null) {
                predicates.add(cb.equal(root.get("dueDate"), filter.dueDate()));
            }
            if (filter.dueDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), filter.dueDateFrom()));
            }
            if (filter.dueDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), filter.dueDateTo()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Applies ORDER BY. Priority is stored as text (LOW/MEDIUM/HIGH), so a plain column sort
     * would be alphabetical; a CASE expression maps it to its logical rank instead.
     * Null due dates are always placed last so they never hide real deadlines.
     */
    public static Specification<Task> sortedBy(TaskSort sort) {
        return (root, query, cb) -> {
            if (query == null) {
                return null;
            }
            boolean asc = sort.direction() == TaskSort.Direction.ASC;
            List<Order> orders = new ArrayList<>();

            switch (sort.field()) {
                case DUE_DATE -> {
                    Path<Object> dueDate = root.get("dueDate");
                    orders.add(cb.asc(cb.selectCase().when(cb.isNull(dueDate), 1).otherwise(0)));
                    orders.add(direction(cb, dueDate, asc));
                }
                case PRIORITY -> orders.add(direction(cb, priorityRank(root, cb), asc));
                case UPDATED_AT -> orders.add(direction(cb, root.get("updatedAt"), asc));
                case CREATED_AT -> orders.add(direction(cb, root.get("createdAt"), asc));
            }

            // Stable tie-breaker so pagination never shuffles rows between pages.
            if (sort.field() != TaskSort.Field.CREATED_AT) {
                orders.add(cb.desc(root.get("createdAt")));
            }

            query.orderBy(orders);
            return null;
        };
    }

    private static Expression<Integer> priorityRank(Root<Task> root, CriteriaBuilder cb) {
        return cb.<Integer>selectCase()
                .when(cb.equal(root.get("priority"), TaskPriority.HIGH), 3)
                .when(cb.equal(root.get("priority"), TaskPriority.MEDIUM), 2)
                .otherwise(1);
    }

    private static Order direction(CriteriaBuilder cb, Expression<?> expression, boolean asc) {
        return asc ? cb.asc(expression) : cb.desc(expression);
    }
}
