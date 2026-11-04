"use client";

import { useApiQuery } from "@/hooks/use-api-query";
import { tasksApi } from "@/lib/api/tasks";
import type { PageResponse } from "@/types/api";
import type { Task, TaskQuery } from "@/types/task";

export function useTasks(query: TaskQuery) {
  const key = JSON.stringify(query);
  return useApiQuery<PageResponse<Task>>((signal) => tasksApi.list(query, signal), key);
}
