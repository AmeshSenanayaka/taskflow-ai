"use client";

import { useState } from "react";
import { Bot, MessagesSquare, Network, Trash2 } from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { ConversationSummary } from "@/types/ai";

interface ConversationListProps {
  conversations: ConversationSummary[];
  activeId: string | null;
  onOpen: (conversation: ConversationSummary) => void;
  onDelete: (conversationId: string) => void;
  error?: string | null;
  disabled?: boolean;
}

const UNTITLED = "New conversation";

function formatWhen(iso: string): string {
  const date = new Date(iso);
  const today = new Date();
  return date.toDateString() === today.toDateString()
    ? date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

/**
 * The signed-in user's stored conversations (Phase 3.4): reopen one to continue it, or delete it together with
 * what the assistant remembers of it.
 */
export function ConversationList({ conversations, activeId, onOpen, onDelete, error, disabled }: ConversationListProps) {
  const [pendingDelete, setPendingDelete] = useState<ConversationSummary | null>(null);

  return (
    <nav aria-label="Conversations" className="flex h-full flex-col">
      <h2 className="flex items-center gap-2 px-3 pt-3 pb-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
        <MessagesSquare className="size-3.5" aria-hidden="true" />
        Conversations
      </h2>
      {error ? <p className="px-3 text-xs text-destructive">{error}</p> : null}
      {conversations.length === 0 && !error ? (
        <p className="px-3 text-xs text-muted-foreground">Your conversations appear here and are kept for 30 days.</p>
      ) : null}
      <ul className="flex-1 overflow-y-auto px-1.5 pb-2" data-testid="conversation-list">
        {conversations.map((conversation) => {
          const active = conversation.conversationId === activeId;
          const title = conversation.title ?? UNTITLED;
          const Icon = conversation.mode === "supervisor" ? Network : Bot;
          return (
            <li key={conversation.conversationId} className="group relative">
              <button
                type="button"
                onClick={() => onOpen(conversation)}
                disabled={disabled}
                aria-current={active ? "true" : undefined}
                className={cn(
                  "flex w-full items-start gap-2 rounded-lg py-2 pr-8 pl-2 text-left text-sm transition-colors",
                  "hover:bg-muted disabled:cursor-not-allowed disabled:opacity-60",
                  active && "bg-muted font-medium",
                )}
              >
                <Icon className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate">{title}</span>
                  <span className="block text-xs text-muted-foreground">{formatWhen(conversation.updatedAt)}</span>
                </span>
              </button>
              <Button
                variant="ghost"
                size="icon"
                className="absolute top-1.5 right-1 size-7 opacity-60 group-hover:opacity-100 focus-visible:opacity-100"
                aria-label={`Delete conversation ${title}`}
                disabled={disabled}
                onClick={() => setPendingDelete(conversation)}
              >
                <Trash2 className="size-3.5" />
              </Button>
            </li>
          );
        })}
      </ul>

      <AlertDialog open={pendingDelete !== null} onOpenChange={(open) => (open ? undefined : setPendingDelete(null))}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this conversation?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingDelete ? (
                <>
                  &ldquo;{pendingDelete.title ?? UNTITLED}&rdquo; and everything the assistant remembers of it will be
                  permanently removed. Your tasks are not affected.
                </>
              ) : null}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={(event) => {
                event.preventDefault();
                if (pendingDelete) onDelete(pendingDelete.conversationId);
                setPendingDelete(null);
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </nav>
  );
}
