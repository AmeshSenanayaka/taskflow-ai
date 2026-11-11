import { apiRequest } from "@/lib/api/client";
import type { AgentDescriptor, AiStatus, ChatRequest, ChatResponse } from "@/types/ai";

/** All LLM traffic stays in the backend; the browser only ever talks to these endpoints. */
export const aiApi = {
  status(signal?: AbortSignal): Promise<AiStatus> {
    return apiRequest<AiStatus>("/api/ai/status", { signal });
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
