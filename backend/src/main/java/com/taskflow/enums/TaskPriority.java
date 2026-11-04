package com.taskflow.enums;

/**
 * Declaration order matters: it is used as the natural sort order
 * when the task list is sorted by priority (LOW &lt; MEDIUM &lt; HIGH).
 */
public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}
