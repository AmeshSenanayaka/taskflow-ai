// These types mirror the backend DTOs in backend/src/main/java/com/taskflow/dto.

export const TASK_STATUSES = ["TODO", "IN_PROGRESS", "COMPLETED"] as const;
export type TaskStatus = (typeof TASK_STATUSES)[number];

export const TASK_PRIORITIES = ["LOW", "MEDIUM", "HIGH"] as const;
export type TaskPriority = (typeof TASK_PRIORITIES)[number];

export const TASK_SORT_FIELDS = ["DUE_DATE", "PRIORITY", "CREATED_AT", "UPDATED_AT"] as const;
export type TaskSortField = (typeof TASK_SORT_FIELDS)[number];

export const SORT_DIRECTIONS = ["ASC", "DESC"] as const;
export type SortDirection = (typeof SORT_DIRECTIONS)[number];

/** Matches TaskResponse. Nullable fields are omitted by the backend when null. */
export interface Task {
  id: string;
  title: string;
  description?: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  /** ISO date, yyyy-MM-dd */
  dueDate?: string | null;
  /** ISO instant */
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
}

/** Matches CreateTaskRequest. */
export interface CreateTaskRequest {
  title: string;
  description?: string | null;
  priority?: TaskPriority;
  dueDate?: string | null;
}

/** Matches UpdateTaskRequest (PUT: every field is replaced). */
export interface UpdateTaskRequest {
  title: string;
  description?: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  dueDate?: string | null;
}

/** Query parameters accepted by GET /api/tasks. */
export interface TaskQuery {
  status?: TaskStatus[];
  priority?: TaskPriority;
  dueDate?: string;
  dueDateFrom?: string;
  dueDateTo?: string;
  search?: string;
  sortBy?: TaskSortField;
  sortDirection?: SortDirection;
  page?: number;
  size?: number;
}
