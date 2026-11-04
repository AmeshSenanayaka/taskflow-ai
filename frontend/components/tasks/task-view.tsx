"use client";

import { useMemo, useState } from "react";
import { useTaskForm, useUserPreferences } from "@/components/providers/app-providers";
import { DeleteTaskDialog } from "@/components/tasks/delete-task-dialog";
import { TaskFilters } from "@/components/tasks/task-filters";
import { TaskList } from "@/components/tasks/task-list";
import { TaskPagination } from "@/components/tasks/task-pagination";
import { useTaskMutations } from "@/hooks/use-task-mutations";
import { useTaskQueryParams, type TaskListParams } from "@/hooks/use-task-query-params";
import { useTasks } from "@/hooks/use-tasks";
import { addDays, todayInTimezone } from "@/lib/dates";
import type { Task, TaskQuery } from "@/types/task";

/** Which slice of tasks a page shows. Resolved against today's date in the user's timezone. */
export type TaskScope = "all" | "today" | "upcoming" | "completed";

const OPEN_STATUSES: TaskQuery["status"] = ["TODO", "IN_PROGRESS"];

function scopeQuery(scope: TaskScope, today: string): Partial<TaskQuery> {
  switch (scope) {
    case "today":
      return { dueDate: today, status: OPEN_STATUSES };
    case "upcoming":
      return { dueDateFrom: addDays(today, 1), status: OPEN_STATUSES };
    case "completed":
      return { status: ["COMPLETED"] };
    default:
      return {};
  }
}

export interface TaskViewProps {
  scope?: TaskScope;
  /** Default values for the URL-backed filter/sort parameters. */
  defaults?: Partial<TaskListParams>;
  showStatusFilter?: boolean;
  emptyTitle: string;
  emptyDescription: string;
}

/**
 * The reusable "task list with filters, sorting and pagination" block used by
 * every list page. Filters live in the URL so views can be bookmarked.
 */
export function TaskView({ scope = "all", defaults, showStatusFilter = true, emptyTitle, emptyDescription }: TaskViewProps) {
  const { timezone } = useUserPreferences();
  const today = todayInTimezone(timezone);
  const { params, setParams, reset } = useTaskQueryParams(defaults);
  const { openCreate, openEdit } = useTaskForm();
  const { changeStatus, deleteTask, isPending } = useTaskMutations();
  const [pendingDelete, setPendingDelete] = useState<Task | null>(null);

  const query = useMemo<TaskQuery>(() => {
    const base = scopeQuery(scope, today);
    return {
      ...base,
      status: params.status ? [params.status] : base.status,
      priority: params.priority || undefined,
      search: params.search || undefined,
      sortBy: params.sortBy,
      sortDirection: params.sortDirection,
      page: params.page,
      size: params.size,
    };
  }, [scope, today, params]);

  const { data, error, isLoading, refetch } = useTasks(query);

  async function handleConfirmDelete(task: Task) {
    const deleted = await deleteTask(task);
    if (deleted) setPendingDelete(null);
  }

  return (
    <div className="space-y-4">
      <TaskFilters params={params} onChange={setParams} onReset={reset} showStatusFilter={showStatusFilter} />

      <TaskList
        tasks={data?.content ?? null}
        today={today}
        isLoading={isLoading}
        error={error}
        onRetry={refetch}
        onEdit={openEdit}
        onDelete={setPendingDelete}
        onStatusChange={changeStatus}
        onAddTask={openCreate}
        emptyTitle={emptyTitle}
        emptyDescription={emptyDescription}
        mutating={isPending}
      />

      {data ? (
        <TaskPagination
          page={data.page}
          size={data.size}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onPageChange={(page) => setParams({ page })}
          onSizeChange={(size) => setParams({ size })}
        />
      ) : null}

      <DeleteTaskDialog task={pendingDelete} onConfirm={handleConfirmDelete} onCancel={() => setPendingDelete(null)} isPending={isPending} />
    </div>
  );
}
