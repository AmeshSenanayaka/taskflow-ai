import { apiRequest, buildQueryString } from "@/lib/api/client";
import type { PageResponse } from "@/types/api";
import type { CreateTaskRequest, Task, TaskQuery, TaskStatus, UpdateTaskRequest } from "@/types/task";

export const tasksApi = {
  list(query: TaskQuery = {}, signal?: AbortSignal): Promise<PageResponse<Task>> {
    return apiRequest<PageResponse<Task>>(`/api/tasks${buildQueryString({ ...query })}`, { signal });
  },

  get(id: string, signal?: AbortSignal): Promise<Task> {
    return apiRequest<Task>(`/api/tasks/${id}`, { signal });
  },

  create(request: CreateTaskRequest): Promise<Task> {
    return apiRequest<Task>("/api/tasks", { method: "POST", body: request });
  },

  update(id: string, request: UpdateTaskRequest): Promise<Task> {
    return apiRequest<Task>(`/api/tasks/${id}`, { method: "PUT", body: request });
  },

  updateStatus(id: string, status: TaskStatus): Promise<Task> {
    return apiRequest<Task>(`/api/tasks/${id}/status`, { method: "PATCH", body: { status } });
  },

  remove(id: string): Promise<void> {
    return apiRequest<void>(`/api/tasks/${id}`, { method: "DELETE" });
  },
};
