import { apiRequest } from "@/lib/api/client";
import type {
  AgentDescriptor,
  AiStatus,
  AssistantMode,
  ChatRequest,
  ChatResponse,
  ConversationMessage,
  ConversationResponse,
  ConversationSummary,
} from "@/types/ai";

/** All LLM traffic stays in the backend; the browser only ever talks to these endpoints. */
export const aiApi = {
  status(signal?: AbortSignal): Promise<AiStatus> {
    return apiRequest<AiStatus>("/api/ai/status", { signal });
  },

  /**
   * Phase 3.0: the server issues conversation ids and records which user owns each one.
   * Phase 3.4: the mode is stored with the conversation, so a resumed conversation reopens in the right mode.
   */
  startConversation(mode: AssistantMode = "supervisor"): Promise<ConversationResponse> {
    return apiRequest<ConversationResponse>(`/api/ai/conversations?mode=${mode}`, { method: "POST" });
  },

  /** Phase 3.4: the signed-in user's stored conversations, most recent first. */
  listConversations(signal?: AbortSignal): Promise<ConversationSummary[]> {
    return apiRequest<ConversationSummary[]>("/api/ai/conversations", { signal });
  },

  /** Phase 3.4: the stored transcript of one conversation (404 if it is not the user's). */
  conversationMessages(conversationId: string, signal?: AbortSignal): Promise<ConversationMessage[]> {
    return apiRequest<ConversationMessage[]>(`/api/ai/conversations/${encodeURIComponent(conversationId)}/messages`, {
      signal,
    });
  },

  /** Phase 3.4: deletes a conversation with its memory and transcript. */
  deleteConversation(conversationId: string): Promise<void> {
    return apiRequest<void>(`/api/ai/conversations/${encodeURIComponent(conversationId)}`, { method: "DELETE" });
  },

  /** Phase 1: one message to the single TaskAgent. */
  chat(request: ChatRequest): Promise<ChatResponse> {
    return apiRequest<ChatResponse>("/api/ai/chat", { method: "POST", body: request });
  },

  /** Phase 2: one message to the TaskFlowSupervisor, which routes to the specialised agents. */
  supervise(request: ChatRequest): Promise<ChatResponse> {
    return apiRequest<ChatResponse>("/api/ai/supervisor/chat", { method: "POST", body: request });
  },

  agents(signal?: AbortSignal): Promise<AgentDescriptor[]> {
    return apiRequest<AgentDescriptor[]>("/api/ai/agents", { signal });
  },

  // Confirmation endpoints are not part of the Phase 1 demo backend yet; approve/reject resolve locally.
  approve(_confirmationId: string, conversationId: string): Promise<ChatResponse> {
    return Promise.resolve({ conversationId, message: "Confirmations are not enabled in this build." });
  },

  reject(_confirmationId: string, conversationId: string): Promise<ChatResponse> {
    return Promise.resolve({ conversationId, message: "Confirmations are not enabled in this build." });
  },
};
