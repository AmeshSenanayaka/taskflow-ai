import type { SortDirection, TaskPriority, TaskSortField, TaskStatus } from "@/types/task";

export const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: "To do",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
};

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
};

export const SORT_FIELD_LABELS: Record<TaskSortField, string> = {
  CREATED_AT: "Created",
  UPDATED_AT: "Last updated",
  DUE_DATE: "Due date",
  PRIORITY: "Priority",
};

export const SORT_DIRECTION_LABELS: Record<SortDirection, string> = {
  ASC: "Ascending",
  DESC: "Descending",
};

/** Tailwind classes for the coloured badges. Kept together so the palette stays consistent. */
export const STATUS_BADGE_CLASSES: Record<TaskStatus, string> = {
  TODO: "border-slate-200 bg-slate-100 text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200",
  IN_PROGRESS: "border-sky-200 bg-sky-100 text-sky-700 dark:border-sky-800 dark:bg-sky-500/20 dark:text-sky-300",
  COMPLETED: "border-emerald-200 bg-emerald-100 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-300",
};

export const PRIORITY_BADGE_CLASSES: Record<TaskPriority, string> = {
  LOW: "border-green-200 bg-green-100 text-green-700 dark:border-green-800 dark:bg-green-500/20 dark:text-green-300",
  MEDIUM: "border-amber-200 bg-amber-100 text-amber-800 dark:border-amber-800 dark:bg-amber-500/20 dark:text-amber-300",
  HIGH: "border-rose-200 bg-rose-100 text-rose-700 dark:border-rose-800 dark:bg-rose-500/20 dark:text-rose-300",
};
