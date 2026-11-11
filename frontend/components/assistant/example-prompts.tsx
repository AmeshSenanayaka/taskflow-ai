"use client";

import { Sparkles } from "lucide-react";
import type { AssistantMode } from "@/types/ai";

export const EXAMPLE_PROMPTS: Record<AssistantMode, readonly string[]> = {
  agent: [
    "Create a high-priority task to review the architecture document tomorrow.",
    "Show my pending tasks.",
    "What tasks are overdue?",
    "Mark my API review task as completed.",
  ],
  supervisor: [
    "Plan my day.",
    "What should I focus on first?",
    "Summarise my progress this week.",
    "Create a high-priority task to review the architecture document tomorrow.",
  ],
};

const COPY: Record<AssistantMode, { title: string; description: string }> = {
  agent: {
    title: "Ask TaskAgent",
    description: "Create, find, update and complete tasks in plain language. Deleting a task always asks for your approval first.",
  },
  supervisor: {
    title: "Ask the TaskFlow Supervisor",
    description:
      "One request, the right specialist: TaskAgent manages tasks, PriorityAgent ranks them, PlanningAgent plans your day and SummaryAgent reports progress.",
  },
};

export function ExamplePrompts({
  mode,
  onPick,
  disabled,
}: {
  mode: AssistantMode;
  onPick: (prompt: string) => void;
  disabled?: boolean;
}) {
  const copy = COPY[mode];
  return (
    <div className="flex h-full flex-col items-center justify-center gap-5 text-center">
      <span className="flex size-12 items-center justify-center rounded-2xl bg-brand-gradient text-white shadow-md shadow-violet-500/30">
        <Sparkles className="size-6" aria-hidden="true" />
      </span>
      <div>
        <h2 className="text-lg font-semibold tracking-tight">{copy.title}</h2>
        <p className="mt-1 max-w-md text-sm text-muted-foreground">{copy.description}</p>
      </div>
      <ul className="grid w-full max-w-xl gap-2 sm:grid-cols-2" aria-label="Example prompts">
        {EXAMPLE_PROMPTS[mode].map((prompt) => (
          <li key={prompt}>
            <button
              type="button"
              onClick={() => onPick(prompt)}
              disabled={disabled}
              className="w-full rounded-xl border border-border/70 bg-card px-3 py-2.5 text-left text-sm shadow-sm transition-colors hover:border-primary/40 hover:bg-accent disabled:opacity-50"
            >
              {prompt}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
