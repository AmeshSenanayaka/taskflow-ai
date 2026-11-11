/** Mirrors the backend DTOs under com.taskflow.ai.dto and com.taskflow.controller.AiChatController. */

export type ChatStatus = "COMPLETED" | "PENDING_CONFIRMATION";

/** Which backend entry point answers: the single Phase 1 TaskAgent or the Phase 2 supervisor. */
export type AssistantMode = "agent" | "supervisor";

/** POST /api/ai/chat and /api/ai/supervisor/chat — the browser generates the conversationId per conversation. */
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

/** One sub-agent invocation made by the supervisor (Phase 2). */
export interface AgentCallInfo {
  name: string;
  durationMs: number;
  success: boolean;
}

export interface AgentExecutionInfo {
  requestId: string;
  durationMs: number;
  toolCalls: ToolCallInfo[];
  /** Present on supervisor responses only. */
  agents?: AgentCallInfo[];
  llmCalls?: number;
  tokenUsage?: { inputTokens: number | null; outputTokens: number | null; totalTokens: number | null } | null;
}

/**
 * The Phase 1 endpoint returns only conversationId + message; the Phase 2 supervisor endpoint also returns
 * dataChanged and execution details. The remaining optional fields are reserved for confirmations.
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

/** GET /api/ai/agents — the specialised agents behind the supervisor. */
export interface AgentDescriptor {
  name: string;
  description: string;
  canModifyTasks: boolean;
}
