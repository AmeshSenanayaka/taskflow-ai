"use client";

import { useCallback, useState } from "react";
import { toast } from "sonner";
import { useDataRefresh } from "@/components/providers/app-providers";
import { getErrorMessage } from "@/lib/api/client";
import { tasksApi } from "@/lib/api/tasks";
import type { CreateTaskRequest, Task, TaskStatus, UpdateTaskRequest } from "@/types/task";

/**
 * Wraps every task mutation with toast feedback and a data refresh so that
 * task lists and dashboard statistics update after each change.
 */
export function useTaskMutations() {
  const { invalidate } = useDataRefresh();
  const [isPending, setIsPending] = useState(false);

  const run = useCallback(
    async <T,>(action: () => Promise<T>, successMessage: string): Promise<T | null> => {
      setIsPending(true);
      try {
        const result = await action();
        invalidate();
        toast.success(successMessage);
        return result;
      } catch (error) {
        toast.error(getErrorMessage(error));
        return null;
      } finally {
        setIsPending(false);
      }
    },
    [invalidate],
  );

  const createTask = useCallback(
    (request: CreateTaskRequest) => run(() => tasksApi.create(request), "Task created"),
    [run],
  );

  const updateTask = useCallback(
    (id: string, request: UpdateTaskRequest) => run(() => tasksApi.update(id, request), "Task updated"),
    [run],
  );

  const changeStatus = useCallback(
    (task: Task, status: TaskStatus) => {
      const message =
        status === "COMPLETED" ? "Task completed" : task.status === "COMPLETED" ? "Task reopened" : "Status updated";
      return run(() => tasksApi.updateStatus(task.id, status), message);
    },
    [run],
  );

  const deleteTask = useCallback(
    (task: Task) => run(() => tasksApi.remove(task.id).then(() => true), "Task deleted"),
    [run],
  );

  return { createTask, updateTask, changeStatus, deleteTask, isPending };
}
