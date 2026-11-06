"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useDataRefresh } from "@/components/providers/app-providers";
import { ApiError, getErrorMessage } from "@/lib/api/client";
import { aiApi } from "@/lib/api/ai";
import type { AgentExecutionInfo, AiStatus, ChatResponse, PendingConfirmation } from "@/types/ai";

export type ConfirmationState = "pending" | "approved" | "rejected" | "expired";

export interface AssistantMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  /** Set on assistant messages that ask the user to approve a destructive operation. */
  confirmation?: PendingConfirmation | null;
  confirmationState?: ConfirmationState;
  execution?: AgentExecutionInfo;
  /** True for user messages that failed to send; they can be retried. */
  failed?: boolean;
}

const STORAGE_KEY = "taskflow.assistant";

interface PersistedState {
  conversationId: string | null;
  messages: AssistantMessage[];
}

function loadPersisted(): PersistedState {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return { conversationId: null, messages: [] };
    const parsed = JSON.parse(raw) as PersistedState;
    return { conversationId: parsed.conversationId ?? null, messages: parsed.messages ?? [] };
  } catch {
    return { conversationId: null, messages: [] };
  }
}

function persist(state: PersistedState) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    /* Storage can be unavailable (private mode); the chat still works for the page lifetime. */
  }
}

function newId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : String(Date.now() + Math.random());
}

/**
 * Chat state for the AI assistant. Keeps the transcript and conversation id in sessionStorage so
 * navigating between pages does not lose context, and bumps the shared data-refresh version after
 * any response that changed task data so lists and statistics stay in sync.
 */
export function useAssistant() {
  const { invalidate } = useDataRefresh();
  const [messages, setMessages] = useState<AssistantMessage[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hydrated = useRef(false);

  // Hydrate from sessionStorage after mount. Reading storage during render would make the
  // statically prerendered HTML differ from the first client render, so this is the one place a
  // setState inside an effect is intended.
  useEffect(() => {
    const persisted = loadPersisted();
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setMessages(persisted.messages);
    setConversationId(persisted.conversationId);
    hydrated.current = true;
  }, []);

  useEffect(() => {
    if (hydrated.current) persist({ conversationId, messages });
  }, [conversationId, messages]);

  useEffect(() => {
    const controller = new AbortController();
    aiApi
      .status(controller.signal)
      .then(setStatus)
      .catch(() => setStatus({ available: false, message: "Cannot reach the TaskFlow backend. Is it running?" }));
    return () => controller.abort();
  }, []);

  const applyResponse = useCallback(
    (response: ChatResponse) => {
      setConversationId(response.conversationId);
      setMessages((current) => [
        ...current,
        {
          id: newId(),
          role: "assistant",
          content: response.message,
          confirmation: response.pendingConfirmation ?? null,
          confirmationState: response.pendingConfirmation ? "pending" : undefined,
          execution: response.execution,
        },
      ]);
      // The backend does not report dataChanged yet, so refresh task lists and stats after every reply.
      if (response.dataChanged ?? true) invalidate();
    },
    [invalidate],
  );

  const send = useCallback(
    async (text: string) => {
      const message = text.trim();
      if (!message || isSending) return;
      setError(null);
      const userMessage: AssistantMessage = { id: newId(), role: "user", content: message };
      setMessages((current) => [...current.filter((item) => !item.failed), userMessage]);
      setIsSending(true);

      // The backend keeps chat memory per conversationId, so the browser owns the id and reuses it
      // for every message until the user starts a new conversation.
      const id = conversationId ?? newId();
      if (!conversationId) setConversationId(id);
      try {
        const response: ChatResponse = await aiApi.chat({ conversationId: id, message });
        applyResponse(response);
      } catch (caught) {
        setError(getErrorMessage(caught));
        setMessages((current) => current.map((item) => (item.id === userMessage.id ? { ...item, failed: true } : item)));
      } finally {
        setIsSending(false);
      }
    },
    [applyResponse, conversationId, isSending],
  );

  const decide = useCallback(
    async (messageId: string, confirmation: PendingConfirmation, decision: "approve" | "reject") => {
      if (!conversationId || isSending) return;
      setError(null);
      setIsSending(true);
      try {
        const response =
          decision === "approve"
            ? await aiApi.approve(confirmation.confirmationId, conversationId)
            : await aiApi.reject(confirmation.confirmationId, conversationId);
        setMessages((current) =>
          current.map((item) =>
            item.id === messageId ? { ...item, confirmationState: decision === "approve" ? "approved" : "rejected" } : item,
          ),
        );
        applyResponse(response);
      } catch (caught) {
        const expired = caught instanceof ApiError && [403, 404, 410].includes(caught.status);
        if (expired) {
          setMessages((current) =>
            current.map((item) => (item.id === messageId ? { ...item, confirmationState: "expired" } : item)),
          );
        }
        setError(getErrorMessage(caught));
      } finally {
        setIsSending(false);
      }
    },
    [applyResponse, conversationId, isSending],
  );

  const newConversation = useCallback(() => {
    setMessages([]);
    setConversationId(null);
    setError(null);
  }, []);

  const retryLast = useCallback(() => {
    const failed = [...messages].reverse().find((item) => item.failed);
    if (failed) void send(failed.content);
  }, [messages, send]);

  return {
    messages,
    conversationId,
    status,
    isSending,
    error,
    send,
    approve: (messageId: string, confirmation: PendingConfirmation) => decide(messageId, confirmation, "approve"),
    reject: (messageId: string, confirmation: PendingConfirmation) => decide(messageId, confirmation, "reject"),
    newConversation,
    retryLast,
  };
}
