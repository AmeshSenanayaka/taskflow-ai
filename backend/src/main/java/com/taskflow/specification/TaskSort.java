package com.taskflow.specification;

/**
 * Sort options supported by the task listing endpoint.
 */
public record TaskSort(Field field, Direction direction) {

    public enum Field {
        DUE_DATE,
        PRIORITY,
        CREATED_AT,
        UPDATED_AT
    }

    public enum Direction {
        ASC,
        DESC
    }

    public static TaskSort defaultSort() {
        return new TaskSort(Field.CREATED_AT, Direction.DESC);
    }
}
