"use client";

import { Check, ShieldAlert, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { ConfirmationState } from "@/hooks/use-assistant";
import { cn } from "@/lib/utils";
import type { PendingConfirmation } from "@/types/ai";

interface ConfirmationCardProps {
  confirmation: PendingConfirmation;
  state: ConfirmationState;
  disabled?: boolean;
  onApprove: () => void;
  onReject: () => void;
}

const STATE_LABELS: Record<Exclude<ConfirmationState, "pending">, string> = {
  approved: "Approved",
  rejected: "Kept",
  expired: "No longer valid",
};

/**
 * The only way a task gets deleted from the chat. The backend created this confirmation; clicking
 * Approve sends its opaque id back, and the backend executes the bound operation exactly once.
 */
export function ConfirmationCard({ confirmation, state, disabled, onApprove, onReject }: ConfirmationCardProps) {
  const pending = state === "pending";
  return (
    <div
      role="group"
      aria-label="Confirmation required"
      data-testid="confirmation-card"
      className={cn(
        "w-full rounded-xl border p-3 text-sm shadow-sm",
        pending ? "border-destructive/40 bg-destructive/5" : "border-border/70 bg-muted/40",
      )}
    >
      <div className="flex items-start gap-2">
        <ShieldAlert className="mt-0.5 size-4 shrink-0 text-destructive" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="font-medium">Delete this task?</p>
          <p className="mt-0.5 truncate text-muted-foreground">“{confirmation.taskTitle}”</p>
          {pending ? (
            <p className="mt-1 text-xs text-muted-foreground">
              This cannot be undone. Nothing is deleted until you approve it here.
            </p>
          ) : (
            <p className="mt-1 text-xs font-medium text-muted-foreground">{STATE_LABELS[state]}</p>
          )}
        </div>
      </div>
      {pending ? (
        <div className="mt-3 flex gap-2">
          <Button size="sm" variant="destructive" onClick={onApprove} disabled={disabled}>
            <Trash2 data-icon="inline-start" />
            Approve deletion
          </Button>
          <Button size="sm" variant="outline" onClick={onReject} disabled={disabled}>
            <X data-icon="inline-start" />
            Keep task
          </Button>
        </div>
      ) : state === "approved" ? (
        <p className="mt-2 inline-flex items-center gap-1 text-xs text-emerald-700 dark:text-emerald-300">
          <Check className="size-3.5" aria-hidden="true" /> Deleted
        </p>
      ) : null}
    </div>
  );
}
