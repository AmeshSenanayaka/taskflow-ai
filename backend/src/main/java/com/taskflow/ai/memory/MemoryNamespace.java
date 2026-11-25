package com.taskflow.ai.memory;

/**
 * Each agent keeps its own memory for a conversation: the supervisor's planner and TaskAgent never read or
 * overwrite each other's window, even though LangChain4j gives both the same {@code @MemoryId}.
 */
public final class MemoryNamespace {

    /** The TaskFlowSupervisor's planner memory (Phase 2 path). */
    public static final String SUPERVISOR = "supervisor";
    /** TaskAgent's memory, both as the Phase 1 agent and as a sub-agent of the supervisor. */
    public static final String TASK_AGENT = "task-agent";

    private MemoryNamespace() {
    }
}
