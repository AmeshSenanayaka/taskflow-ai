"use client";

import { Bot, Network } from "lucide-react";
import { cn } from "@/lib/utils";
import type { AssistantMode } from "@/types/ai";

export const MODE_LABELS: Record<AssistantMode, string> = {
  agent: "TaskAgent",
  supervisor: "Supervisor",
};

const MODE_HELP: Record<AssistantMode, string> = {
  agent: "One agent that creates, finds and updates tasks (Phase 1).",
  supervisor: "Routes to TaskAgent, PriorityAgent, PlanningAgent and SummaryAgent (Phase 2).",
};

interface ModeSwitchProps {
  mode: AssistantMode;
  onChange: (mode: AssistantMode) => void;
  disabled?: boolean;
}

/** Segmented control choosing which backend entry point answers the next message. */
export function ModeSwitch({ mode, onChange, disabled }: ModeSwitchProps) {
  return (
    <div role="radiogroup" aria-label="Assistant mode" className="inline-flex rounded-lg border border-border/70 bg-muted/40 p-0.5">
      {(["supervisor", "agent"] as const).map((option) => {
        const selected = option === mode;
        const Icon = option === "supervisor" ? Network : Bot;
        return (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={selected}
            title={selected ? MODE_HELP[option] : `${MODE_HELP[option]} Switching starts a new conversation.`}
            disabled={disabled}
            onClick={() => onChange(option)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors disabled:opacity-50",
              selected ? "bg-brand-gradient text-white shadow-sm" : "text-muted-foreground hover:text-foreground",
            )}
          >
            <Icon className="size-3.5" aria-hidden="true" />
            {MODE_LABELS[option]}
          </button>
        );
      })}
    </div>
  );
}
