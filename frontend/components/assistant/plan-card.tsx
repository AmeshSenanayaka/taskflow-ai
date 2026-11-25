import { CalendarClock } from "lucide-react";
import { PRIORITY_LABELS } from "@/lib/task-labels";
import type { DailyPlan } from "@/types/ai";

/**
 * The day plan exactly as the backend's plan rules accepted it (Phase 3.2): it always fits the daily available
 * minutes and only contains the user's open tasks, whatever the model wrote in its answer.
 */
export function PlanCard({ plan }: { plan: DailyPlan }) {
  return (
    <section
      data-testid="plan-card"
      aria-label={`Plan for ${plan.date}`}
      className="w-full rounded-xl border border-border/70 bg-card p-3 text-sm shadow-sm"
    >
      <header className="mb-2 flex items-center gap-2 font-medium">
        <CalendarClock className="size-4 text-muted-foreground" aria-hidden="true" />
        <span>Plan for {plan.date}</span>
        <span className="ml-auto text-xs font-normal text-muted-foreground">
          {plan.totalMinutes} of {plan.availableMinutes} min · checked
        </span>
      </header>
      <ol className="space-y-1">
        {plan.entries.map((entry) => (
          <li key={entry.taskId} className="flex items-baseline gap-3">
            <span className="w-24 shrink-0 font-mono text-xs tabular-nums text-muted-foreground">
              {entry.start}–{entry.end}
            </span>
            <span className="flex-1">{entry.title}</span>
            <span className="text-xs text-muted-foreground">
              {PRIORITY_LABELS[entry.priority]} · {entry.minutes} min
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
