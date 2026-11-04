"use client";

import { AlertTriangle } from "lucide-react";
import { EmptyState } from "@/components/tasks/empty-state";
import { TaskCard } from "@/components/tasks/task-card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getErrorMessage } from "@/lib/api/client";
import type { Task, TaskStatus } from "@/types/task";

export interface TaskListProps {
  tasks: Task[] | null;
  today: string;
  isLoading: boolean;
  error: unknown;
  onRetry: () => void;
  onEdit: (task: Task) => void;
  onDelete: (task: Task) => void;
  onStatusChange: (task: Task, status: TaskStatus) => void;
  onAddTask?: () => void;
  emptyTitle: string;
  emptyDescription: string;
  mutating?: boolean;
}

function TaskListSkeleton() {
  return (
    <div className="space-y-3" aria-busy="true" aria-label="Loading tasks">
      {Array.from({ length: 4 }).map((_, index) => (
        <div key={index} className="rounded-xl border bg-background p-4 shadow-sm">
          <div className="flex items-start gap-3">
            <Skeleton className="size-7 rounded-full" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-4 w-1/2" />
              <div className="flex gap-2 pt-1">
                <Skeleton className="h-5 w-20 rounded-full" />
                <Skeleton className="h-5 w-16 rounded-full" />
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export function TaskList({
  tasks,
  today,
  isLoading,
  error,
  onRetry,
  onEdit,
  onDelete,
  onStatusChange,
  onAddTask,
  emptyTitle,
  emptyDescription,
  mutating,
}: TaskListProps) {
  if (isLoading && !tasks) {
    return <TaskListSkeleton />;
  }

  if (error && !tasks) {
    return (
      <div role="alert" className="flex items-center gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm">
        <AlertTriangle className="size-4 text-destructive" aria-hidden="true" />
        <span className="flex-1">Could not load tasks: {getErrorMessage(error)}</span>
        <Button variant="outline" size="sm" onClick={onRetry}>
          Retry
        </Button>
      </div>
    );
  }

  if (!tasks || tasks.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} onAddTask={onAddTask} />;
  }

  return (
    <ul className="space-y-3" aria-busy={isLoading || mutating ? "true" : undefined}>
      {tasks.map((task) => (
        <li key={task.id}>
          <TaskCard task={task} today={today} onEdit={onEdit} onDelete={onDelete} onStatusChange={onStatusChange} disabled={mutating} />
        </li>
      ))}
    </ul>
  );
}
