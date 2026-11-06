import { apiRequest } from "@/lib/api/client";
import type { AiStatus, ChatRequest, ChatResponse } from "@/types/ai";

/** All LLM traffic stays in the backend; the browser only ever talks to these endpoints. */
export const aiApi = {
  status(signal?: AbortSignal): Promise<AiStatus> {
    return apiRequest<AiStatus>("/api/ai/status", { signal });
  },

  chat(request: ChatRequest): Promise<ChatResponse> {
    return apiRequest<ChatResponse>("/api/ai/chat", { method: "POST", body: request });
  },

  // Confirmation endpoints are not part of the Phase 1 demo backend yet; approve/reject resolve locally.
  approve(_confirmationId: string, conversationId: string): Promise<ChatResponse> {
    return Promise.resolve({ conversationId, message: "Confirmations are not enabled in this build." });
  },

  reject(_confirmationId: string, conversationId: string): Promise<ChatResponse> {
    return Promise.resolve({ conversationId, message: "Confirmations are not enabled in this build." });
  },
};
