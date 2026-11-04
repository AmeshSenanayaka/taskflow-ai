"use client";

import { AlertTriangle, CheckCircle2, CircleDashed, ListTodo, Loader } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useDashboardStats } from "@/hooks/use-dashboard-stats";
import { getErrorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import type { DashboardStats } from "@/types/dashboard";

interface StatCardProps {
  label: string;
  value: number | null;
  icon: typeof ListTodo;
  accent: string;
  hint?: string;
}

function StatCard({ label, value, icon: Icon, accent, hint }: StatCardProps) {
  return (
    <Card className="rounded-xl border-border/70 shadow-sm shadow-violet-900/5">
      <CardContent className="flex items-center gap-4 p-5">
        <span className={cn("flex size-11 shrink-0 items-center justify-center rounded-lg", accent)}>
          <Icon className="size-5" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{label}</p>
          {value === null ? (
            <Skeleton className="mt-1 h-7 w-12" />
          ) : (
            <p className="text-2xl font-semibold tabular-nums" data-testid={`stat-${label.toLowerCase().replace(/\s+/g, "-")}`}>
              {value}
            </p>
          )}
          {hint ? <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p> : null}
        </div>
      </CardContent>
    </Card>
  );
}

function describeAttention(stats: DashboardStats | null): string | undefined {
  if (!stats) return undefined;
  const parts: string[] = [];
  if (stats.overdue > 0) parts.push(`${stats.overdue} overdue`);
  if (stats.dueToday > 0) parts.push(`${stats.dueToday} due today`);
  return parts.length ? parts.join(" · ") : "Nothing overdue";
}

export function StatsCards() {
  const { data, error, isLoading, refetch } = useDashboardStats();
  const stats = data;
  const showSkeleton = isLoading && !stats;

  if (error && !stats) {
    return (
      <div role="alert" className="flex items-center gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm">
        <AlertTriangle className="size-4 text-destructive" aria-hidden="true" />
        <span className="flex-1">Could not load statistics: {getErrorMessage(error)}</span>
        <Button variant="outline" size="sm" onClick={refetch}>
          Retry
        </Button>
      </div>
    );
  }

  return (
    <section aria-label="Statistics" className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <StatCard
        label="Total Tasks"
        value={showSkeleton ? null : (stats?.total ?? 0)}
        icon={ListTodo}
        accent="bg-violet-100 text-violet-700 dark:bg-violet-500/20 dark:text-violet-300"
        hint={describeAttention(stats)}
      />
      <StatCard label="Pending" value={showSkeleton ? null : (stats?.pending ?? 0)} icon={CircleDashed} accent="bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300" />
      <StatCard label="In Progress" value={showSkeleton ? null : (stats?.inProgress ?? 0)} icon={Loader} accent="bg-sky-100 text-sky-700 dark:bg-sky-500/20 dark:text-sky-300" />
      <StatCard label="Completed" value={showSkeleton ? null : (stats?.completed ?? 0)} icon={CheckCircle2} accent="bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300" />
    </section>
  );
}
