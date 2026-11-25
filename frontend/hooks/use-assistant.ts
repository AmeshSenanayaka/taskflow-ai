"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useDataRefresh } from "@/components/providers/app-providers";
import { ApiError, getErrorMessage } from "@/lib/api/client";
import { aiApi } from "@/lib/api/ai";
import type {
  AgentExecutionInfo,
  AiStatus,
  AssistantMode,
  ChatResponse,
  ConversationMessage,
  ConversationSummary,
  DailyPlan,
  PendingConfirmation,
} from "@/types/ai";

export type ConfirmationState = "pending" | "approved" | "rejected" | "expired";

export interface AssistantMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  /** Which entry point produced an assistant message (absent on user messages). */
  mode?: AssistantMode;
  /** Set on assistant messages that ask the user to approve a destructive operation. */
  confirmation?: PendingConfirmation | null;
  confirmationState?: ConfirmationState;
  execution?: AgentExecutionInfo;
  /** A rule-checked day plan returned with the answer (Phase 3.2). */
  plan?: DailyPlan | null;
  /** Guardrails that acted on this answer (Phase 3.3). */
  guardrails?: string[];
  /** True for user messages that failed to send; they can be retried. */
  failed?: boolean;
}

const STORAGE_KEY = "taskflow.assistant";

interface PersistedState {
  conversationId: string | null;
  messages: AssistantMessage[];
  mode: AssistantMode;
}

const DEFAULT_MODE: AssistantMode = "supervisor";

function loadPersisted(): PersistedState {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return { conversationId: null, messages: [], mode: DEFAULT_MODE };
    const parsed = JSON.parse(raw) as Partial<PersistedState>;
    return {
      conversationId: parsed.conversationId ?? null,
      messages: parsed.messages ?? [],
      mode: parsed.mode === "agent" || parsed.mode === "supervisor" ? parsed.mode : DEFAULT_MODE,
    };
  } catch {
    return { conversationId: null, messages: [], mode: DEFAULT_MODE };
  }
}

function persist(state: PersistedState) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    /* Storage can be unavailable (private mode); the chat still works for the page lifetime. */
  }
}

/** A stored transcript line as a chat message (Phase 3.4: resuming a conversation). */
function fromTranscript(message: ConversationMessage, mode: AssistantMode): AssistantMessage {
  return {
    id: newId(),
    role: message.role,
    content: message.content,
    mode: message.role === "assistant" ? mode : undefined,
    plan: message.plan ?? null,
    guardrails: message.guardrails ?? [],
  };
}

function newId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : String(Date.now() + Math.random());
}

/**
 * Chat state for the AI assistant. Keeps the transcript, conversation id and mode in sessionStorage so
 * navigating between pages does not lose context, and bumps the shared data-refresh version after
 * any response that changed task data so lists and statistics stay in sync.
 *
 * Two modes: "agent" talks to the single Phase 1 TaskAgent, "supervisor" to the Phase 2 TaskFlowSupervisor
 * that routes to TaskAgent, PriorityAgent, PlanningAgent and SummaryAgent. The backend keeps a separate chat
 * memory per entry point, so switching mode starts a new conversation instead of showing a transcript the
 * other agent has never seen.
 *
 * Phase 3.4: conversations are stored on the server. The hook lists them, reopens one (its transcript and mode)
 * and deletes them; the model's memory of a reopened conversation is restored by the backend, not by the browser.
 */
export function useAssistant() {
  const { invalidate } = useDataRefresh();
  const [messages, setMessages] = useState<AssistantMessage[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [mode, setMode] = useState<AssistantMode>(DEFAULT_MODE);
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [conversationsError, setConversationsError] = useState<string | null>(null);
  const [isOpening, setIsOpening] = useState(false);
  const hydrated = useRef(false);

  const refreshConversations = useCallback(async () => {
    try {
      setConversations(await aiApi.listConversations());
      setConversationsError(null);
    } catch (caught) {
      setConversationsError(getErrorMessage(caught));
    }
  }, []);

  useEffect(() => {
    void refreshConversations();
  }, [refreshConversations]);

  // Hydrate from sessionStorage after mount. Reading storage during render would make the
  // statically prerendered HTML differ from the first client render, so this is the one place a
  // setState inside an effect is intended.
  useEffect(() => {
    const persisted = loadPersisted();
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setMessages(persisted.messages);
    setConversationId(persisted.conversationId);
    setMode(persisted.mode);
    hydrated.current = true;
  }, []);

  useEffect(() => {
    if (hydrated.current) persist({ conversationId, messages, mode });
  }, [conversationId, messages, mode]);

  useEffect(() => {
    const controller = new AbortController();
    aiApi
      .status(controller.signal)
      .then(setStatus)
      .catch(() => setStatus({ available: false, message: "Cannot reach the TaskFlow backend. Is it running?" }));
    return () => controller.abort();
  }, []);

  const applyResponse = useCallback(
    (response: ChatResponse, responseMode: AssistantMode) => {
      setConversationId(response.conversationId);
      setMessages((current) => [
        ...current,
        {
          id: newId(),
          role: "assistant",
          mode: responseMode,
          content: response.message,
          confirmation: response.pendingConfirmation ?? null,
          confirmationState: response.pendingConfirmation ? "pending" : undefined,
          execution: response.execution,
          plan: response.plan ?? null,
          guardrails: response.guardrails ?? [],
        },
      ]);
      // The Phase 1 endpoint does not report dataChanged, so refresh after every reply in that case.
      if (response.dataChanged ?? true) invalidate();
      // The conversation's title and last activity changed.
      void refreshConversations();
    },
    [invalidate, refreshConversations],
  );

  const send = useCallback(
    async (text: string) => {
      const message = text.trim();
      if (!message || isSending) return;
      setError(null);
      const userMessage: AssistantMessage = { id: newId(), role: "user", content: message };
      setMessages((current) => [...current.filter((item) => !item.failed), userMessage]);
      setIsSending(true);

      // The backend keeps chat memory per conversationId. Since Phase 3.0 the server issues the id (and records
      // who owns it); the browser asks for one on the first message and reuses it until a new conversation starts.
      try {
        let id = conversationId;
        if (!id) {
          id = (await aiApi.startConversation(mode)).conversationId;
          setConversationId(id);
        }
        const request = { conversationId: id, message };
        const response: ChatResponse = mode === "supervisor" ? await aiApi.supervise(request) : await aiApi.chat(request);
        applyResponse(response, mode);
      } catch (caught) {
        setError(getErrorMessage(caught));
        setMessages((current) => current.map((item) => (item.id === userMessage.id ? { ...item, failed: true } : item)));
        // 404: the conversation is unknown to the server or belongs to another user; a retry starts a new one.
        if (caught instanceof ApiError && caught.isNotFound) setConversationId(null);
        // A supervisor turn can fail after TaskAgent already changed data (a later LLM call timing out), and the
        // error response carries no dataChanged flag. Refresh so the lists show what was saved before a retry.
        if (mode === "supervisor") invalidate();
      } finally {
        setIsSending(false);
      }
    },
    [applyResponse, conversationId, invalidate, isSending, mode],
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
        applyResponse(response, mode);
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
    [applyResponse, conversationId, isSending, mode],
  );

  const newConversation = useCallback(() => {
    setMessages([]);
    setConversationId(null);
    setError(null);
  }, []);

  const changeMode = useCallback(
    (next: AssistantMode) => {
      if (next === mode) return;
      setMode(next);
      // Each entry point has its own chat memory on the backend: a follow-up such as "change its priority"
      // would not resolve against messages the other agent never received.
      newConversation();
    },
    [mode, newConversation],
  );

  /** Reopens a stored conversation: its transcript, its mode and (on the server) the model's memory of it. */
  const openConversation = useCallback(
    async (summary: ConversationSummary) => {
      if (isSending || isOpening) return;
      setError(null);
      setIsOpening(true);
      try {
        const transcript = await aiApi.conversationMessages(summary.conversationId);
        setMode(summary.mode);
        setConversationId(summary.conversationId);
        setMessages(transcript.map((message) => fromTranscript(message, summary.mode)));
      } catch (caught) {
        setError(getErrorMessage(caught));
        // Gone (deleted elsewhere or expired by retention): drop it from the list.
        if (caught instanceof ApiError && caught.isNotFound) void refreshConversations();
      } finally {
        setIsOpening(false);
      }
    },
    [isOpening, isSending, refreshConversations],
  );

  /** Deletes a stored conversation with its memory and transcript; the open one is closed. */
  const deleteConversation = useCallback(
    async (id: string) => {
      setError(null);
      try {
        await aiApi.deleteConversation(id);
        if (id === conversationId) newConversation();
      } catch (caught) {
        if (!(caught instanceof ApiError && caught.isNotFound)) setError(getErrorMessage(caught));
      } finally {
        void refreshConversations();
      }
    },
    [conversationId, newConversation, refreshConversations],
  );

  const retryLast = useCallback(() => {
    const failed = [...messages].reverse().find((item) => item.failed);
    if (failed) void send(failed.content);
  }, [messages, send]);

  return {
    messages,
    conversationId,
    mode,
    setMode: changeMode,
    status,
    isSending,
    error,
    send,
    approve: (messageId: string, confirmation: PendingConfirmation) => decide(messageId, confirmation, "approve"),
    reject: (messageId: string, confirmation: PendingConfirmation) => decide(messageId, confirmation, "reject"),
    newConversation,
    retryLast,
    conversations,
    conversationsError,
    isOpening,
    openConversation,
    deleteConversation,
    refreshConversations,
  };
}
