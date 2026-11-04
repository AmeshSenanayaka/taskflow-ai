"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useMemo } from "react";
import {
  SORT_DIRECTIONS,
  TASK_PRIORITIES,
  TASK_SORT_FIELDS,
  TASK_STATUSES,
  type SortDirection,
  type TaskPriority,
  type TaskSortField,
  type TaskStatus,
} from "@/types/task";

export interface TaskListParams {
  status: TaskStatus | "";
  priority: TaskPriority | "";
  search: string;
  sortBy: TaskSortField;
  sortDirection: SortDirection;
  page: number;
  size: number;
}

export const DEFAULT_PAGE_SIZE = 10;

function isOneOf<T extends string>(values: readonly T[], value: string | null): value is T {
  return value !== null && (values as readonly string[]).includes(value);
}

/**
 * Keeps filters, sorting and pagination in the URL query string so views are
 * shareable and survive a refresh. Changing a filter resets the page to 0.
 */
export function useTaskQueryParams(defaults: Partial<TaskListParams> = {}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const params = useMemo<TaskListParams>(() => {
    const status = searchParams.get("status");
    const priority = searchParams.get("priority");
    const sortBy = searchParams.get("sortBy");
    const sortDirection = searchParams.get("sortDirection");
    const page = Number(searchParams.get("page") ?? "0");
    const size = Number(searchParams.get("size") ?? String(DEFAULT_PAGE_SIZE));

    return {
      status: isOneOf(TASK_STATUSES, status) ? status : (defaults.status ?? ""),
      priority: isOneOf(TASK_PRIORITIES, priority) ? priority : (defaults.priority ?? ""),
      search: searchParams.get("search") ?? defaults.search ?? "",
      sortBy: isOneOf(TASK_SORT_FIELDS, sortBy) ? sortBy : (defaults.sortBy ?? "CREATED_AT"),
      sortDirection: isOneOf(SORT_DIRECTIONS, sortDirection) ? sortDirection : (defaults.sortDirection ?? "DESC"),
      page: Number.isInteger(page) && page >= 0 ? page : 0,
      size: Number.isInteger(size) && size > 0 && size <= 100 ? size : DEFAULT_PAGE_SIZE,
    };
  }, [searchParams, defaults.status, defaults.priority, defaults.search, defaults.sortBy, defaults.sortDirection]);

  const setParams = useCallback(
    (changes: Partial<TaskListParams>) => {
      const next = new URLSearchParams(searchParams.toString());
      const merged = { ...params, ...changes };
      if (!("page" in changes)) merged.page = 0;

      const write = (key: string, value: string | number, defaultValue: string | number) => {
        if (value === "" || value === defaultValue) next.delete(key);
        else next.set(key, String(value));
      };
      write("status", merged.status, defaults.status ?? "");
      write("priority", merged.priority, defaults.priority ?? "");
      write("search", merged.search, defaults.search ?? "");
      write("sortBy", merged.sortBy, defaults.sortBy ?? "CREATED_AT");
      write("sortDirection", merged.sortDirection, defaults.sortDirection ?? "DESC");
      write("page", merged.page, 0);
      write("size", merged.size, DEFAULT_PAGE_SIZE);

      const query = next.toString();
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
    },
    [router, pathname, searchParams, params, defaults],
  );

  const reset = useCallback(() => router.replace(pathname, { scroll: false }), [router, pathname]);

  return { params, setParams, reset };
}
