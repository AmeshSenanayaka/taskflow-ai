"use client";

import { Sparkles } from "lucide-react";

export const EXAMPLE_PROMPTS = [
  "Create a high-priority task to review the architecture document tomorrow.",
  "Show my pending tasks.",
  "What tasks are overdue?",
  "Mark my API review task as completed.",
] as const;

export function ExamplePrompts({ onPick, disabled }: { onPick: (prompt: string) => void; disabled?: boolean }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-5 text-center">
      <span className="flex size-12 items-center justify-center rounded-2xl bg-brand-gradient text-white shadow-md shadow-violet-500/30">
        <Sparkles className="size-6" aria-hidden="true" />
      </span>
      <div>
        <h2 className="text-lg font-semibold tracking-tight">Ask TaskAgent</h2>
        <p className="mt-1 max-w-md text-sm text-muted-foreground">
          Create, find, update and complete tasks in plain language. Deleting a task always asks for your approval first.
        </p>
      </div>
      <ul className="grid w-full max-w-xl gap-2 sm:grid-cols-2" aria-label="Example prompts">
        {EXAMPLE_PROMPTS.map((prompt) => (
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
