/** Mirrors the backend DTOs under com.taskflow.ai.dto. */

export type ChatStatus = "COMPLETED" | "PENDING_CONFIRMATION";

/** POST /api/ai/chat — the backend requires a conversationId; the browser generates one per conversation. */
export interface ChatRequest {
  conversationId: string;
  message: string;
}

export interface PendingConfirmation {
  confirmationId: string;
  operation: "DELETE_TASK";
  taskId: string;
  taskTitle: string;
  expiresAt: string;
}

export interface ToolCallInfo {
  name: string;
  durationMs: number;
  success: boolean;
}

export interface AgentExecutionInfo {
  requestId: string;
  durationMs: number;
  toolCalls: ToolCallInfo[];
  tokenUsage?: { inputTokens: number | null; outputTokens: number | null; totalTokens: number | null } | null;
}

/**
 * The Phase 1 backend returns only conversationId + message. The optional fields are reserved for the
 * richer contract (confirmations, execution details) and are simply absent until the backend sends them.
 */
export interface ChatResponse {
  conversationId: string;
  message: string;
  status?: ChatStatus;
  pendingConfirmation?: PendingConfirmation | null;
  dataChanged?: boolean;
  execution?: AgentExecutionInfo;
}

export interface ConversationResponse {
  conversationId: string;
  createdAt: string;
}

export interface AiStatus {
  available: boolean;
  model?: string | null;
  message?: string | null;
}
