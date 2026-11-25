"use client";

import { useCallback, useState } from "react";
import { toast } from "sonner";
import { useDataRefresh } from "@/components/providers/app-providers";
import { ApiError, getErrorMessage } from "@/lib/api/client";
import { tasksApi } from "@/lib/api/tasks";
import type { CreateTaskRequest, Task, TaskStatus, UpdateTaskRequest } from "@/types/task";

/** Lets a form receive the server's per-field messages (validation 400s and business-rule 422s). */
export interface MutationOptions {
  onFieldErrors?: (errors: Record<string, string>) => void;
}

/**
 * Wraps every task mutation with toast feedback and a data refresh so that
 * task lists and dashboard statistics update after each change.
 */
export function useTaskMutations() {
  const { invalidate } = useDataRefresh();
  const [isPending, setIsPending] = useState(false);

  const run = useCallback(
    async <T,>(action: () => Promise<T>, successMessage: string, options?: MutationOptions): Promise<T | null> => {
      setIsPending(true);
      try {
        const result = await action();
        invalidate();
        toast.success(successMessage);
        return result;
      } catch (error) {
        if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
          options?.onFieldErrors?.(error.fieldErrors);
        }
        toast.error(getErrorMessage(error));
        return null;
      } finally {
        setIsPending(false);
      }
    },
    [invalidate],
  );

  const createTask = useCallback(
    (request: CreateTaskRequest, options?: MutationOptions) => run(() => tasksApi.create(request), "Task created", options),
    [run],
  );

  const updateTask = useCallback(
    (id: string, request: UpdateTaskRequest, options?: MutationOptions) =>
      run(() => tasksApi.update(id, request), "Task updated", options),
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
