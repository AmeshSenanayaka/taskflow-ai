"use client";

import { useEffect, useState } from "react";
import { ArrowDownAZ, ArrowUpAZ, Search, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { TaskListParams } from "@/hooks/use-task-query-params";
import { PRIORITY_LABELS, SORT_FIELD_LABELS, STATUS_LABELS } from "@/lib/task-labels";
import { TASK_PRIORITIES, TASK_SORT_FIELDS, TASK_STATUSES } from "@/types/task";

const ALL = "ALL";

interface TaskFiltersProps {
  params: TaskListParams;
  onChange: (changes: Partial<TaskListParams>) => void;
  onReset: () => void;
  showStatusFilter?: boolean;
}

export function TaskFilters({ params, onChange, onReset, showStatusFilter = true }: TaskFiltersProps) {
  // Local text state so typing feels instant; the URL (and the request) update after a short debounce.
  const [searchText, setSearchText] = useState(params.search);
  const [syncedSearch, setSyncedSearch] = useState(params.search);
  if (syncedSearch !== params.search) {
    setSyncedSearch(params.search);
    setSearchText(params.search);
  }

  useEffect(() => {
    if (searchText === params.search) return;
    const timeout = setTimeout(() => onChange({ search: searchText }), 300);
    return () => clearTimeout(timeout);
  }, [searchText, params.search, onChange]);

  const hasActiveFilters = params.search !== "" || params.priority !== "" || (showStatusFilter && params.status !== "");

  return (
    <div className="flex flex-col gap-3 md:flex-row md:flex-wrap md:items-center">
      <div className="relative min-w-0 flex-1 md:max-w-sm">
        <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
        <Input
          type="search"
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          placeholder="Search tasks…"
          aria-label="Search tasks"
          className="pl-8"
        />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {showStatusFilter ? (
          <Select value={params.status || ALL} onValueChange={(value) => onChange({ status: value === ALL ? "" : (value as TaskListParams["status"]) })}>
            <SelectTrigger aria-label="Filter by status" className="w-[150px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All statuses</SelectItem>
              {TASK_STATUSES.map((status) => (
                <SelectItem key={status} value={status}>
                  {STATUS_LABELS[status]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}

        <Select value={params.priority || ALL} onValueChange={(value) => onChange({ priority: value === ALL ? "" : (value as TaskListParams["priority"]) })}>
          <SelectTrigger aria-label="Filter by priority" className="w-[150px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All priorities</SelectItem>
            {TASK_PRIORITIES.map((priority) => (
              <SelectItem key={priority} value={priority}>
                {PRIORITY_LABELS[priority]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={params.sortBy} onValueChange={(value) => onChange({ sortBy: value as TaskListParams["sortBy"] })}>
          <SelectTrigger aria-label="Sort by" className="w-[150px]">
            <span className="text-muted-foreground">Sort:</span>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {TASK_SORT_FIELDS.map((field) => (
              <SelectItem key={field} value={field}>
                {SORT_FIELD_LABELS[field]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Button
          variant="outline"
          size="icon"
          aria-label={params.sortDirection === "ASC" ? "Sorted ascending, switch to descending" : "Sorted descending, switch to ascending"}
          onClick={() => onChange({ sortDirection: params.sortDirection === "ASC" ? "DESC" : "ASC" })}
        >
          {params.sortDirection === "ASC" ? <ArrowUpAZ /> : <ArrowDownAZ />}
        </Button>

        {hasActiveFilters ? (
          <Button variant="ghost" size="sm" onClick={onReset}>
            <X data-icon="inline-start" />
            Clear
          </Button>
        ) : null}
      </div>
    </div>
  );
}
