"use client";

import { Bot, User } from "lucide-react";
import { ConfirmationCard } from "@/components/assistant/confirmation-card";
import type { AssistantMessage } from "@/hooks/use-assistant";
import { cn } from "@/lib/utils";
import type { PendingConfirmation } from "@/types/ai";

interface MessageBubbleProps {
  message: AssistantMessage;
  onApprove: (messageId: string, confirmation: PendingConfirmation) => void;
  onReject: (messageId: string, confirmation: PendingConfirmation) => void;
  disabled?: boolean;
}

function describeExecution(message: AssistantMessage): string | null {
  const execution = message.execution;
  if (!execution) return null;
  const parts: string[] = [];
  if (execution.agents && execution.agents.length > 0) {
    parts.push(
      `Agents: ${execution.agents.map((call) => `${call.name} (${call.durationMs} ms${call.success ? "" : ", failed"})`).join(" > ")}`,
    );
  } else if (execution.agents && message.mode === "supervisor") {
    parts.push("Answered by the supervisor directly");
  }
  if (execution.toolCalls.length > 0) {
    parts.push(
      `Tools: ${execution.toolCalls.map((call) => `${call.name} (${call.durationMs} ms${call.success ? "" : ", failed"})`).join(", ")}`,
    );
  }
  if (execution.llmCalls) parts.push(`${execution.llmCalls} LLM call${execution.llmCalls === 1 ? "" : "s"}`);
  if (execution.tokenUsage?.totalTokens) parts.push(`${execution.tokenUsage.totalTokens} tokens`);
  parts.push(`${execution.durationMs} ms`);
  return parts.join(" · ");
}

export function MessageBubble({ message, onApprove, onReject, disabled }: MessageBubbleProps) {
  const isUser = message.role === "user";
  const execution = describeExecution(message);

  return (
    <div
      data-testid={`message-${message.role}`}
      className={cn("flex items-end gap-2", isUser ? "flex-row-reverse" : "flex-row")}
    >
      <span
        aria-hidden="true"
        className={cn(
          "flex size-8 shrink-0 items-center justify-center rounded-full",
          isUser ? "bg-secondary text-secondary-foreground" : "bg-brand-gradient text-white",
        )}
      >
        {isUser ? <User className="size-4" /> : <Bot className="size-4" />}
      </span>

      <div className={cn("flex max-w-[85%] flex-col gap-2", isUser ? "items-end" : "items-start")}>
        <div
          className={cn(
            "whitespace-pre-wrap rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-sm",
            isUser
              ? "rounded-br-md bg-brand-gradient text-white"
              : "rounded-bl-md border border-border/70 bg-card text-card-foreground",
            message.failed && "border border-destructive/40 opacity-70",
          )}
        >
          {message.content}
        </div>

        {message.confirmation ? (
          <ConfirmationCard
            confirmation={message.confirmation}
            state={message.confirmationState ?? "pending"}
            disabled={disabled}
            onApprove={() => onApprove(message.id, message.confirmation!)}
            onReject={() => onReject(message.id, message.confirmation!)}
          />
        ) : null}

        {execution ? (
          <details className="text-xs text-muted-foreground">
            <summary className="cursor-pointer select-none">Execution details</summary>
            <p className="mt-1 font-mono text-[11px]">
              {execution}
              {message.execution ? ` · request ${message.execution.requestId.slice(0, 8)}` : null}
            </p>
          </details>
        ) : null}
        {message.failed ? <p className="text-xs text-destructive">Not sent</p> : null}
      </div>
    </div>
  );
}
