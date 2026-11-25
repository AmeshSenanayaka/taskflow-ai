/** Mirrors the backend DTOs under com.taskflow.ai.dto and com.taskflow.controller.AiChatController. */

export type ChatStatus = "COMPLETED" | "PENDING_CONFIRMATION";

/** Which backend entry point answers: the single Phase 1 TaskAgent or the Phase 2 supervisor. */
export type AssistantMode = "agent" | "supervisor";

/** POST /api/ai/conversations — a server-issued conversation id owned by the signed-in user (Phase 3.0). */
export interface ConversationResponse {
  conversationId: string;
}

/** POST /api/ai/chat and /api/ai/supervisor/chat — conversationId comes from POST /api/ai/conversations. */
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
  /** Phase 3.2: the rule-checked day plan, when the PlanningAgent produced one. */
  plan?: DailyPlan | null;
}

/** Mirrors com.taskflow.ai.dto.DailyPlan: a plan that passed every plan rule. */
export interface DailyPlan {
  date: string;
  availableMinutes: number;
  totalMinutes: number;
  entries: DailyPlanEntry[];
}

export interface DailyPlanEntry {
  taskId: string;
  title: string;
  priority: "LOW" | "MEDIUM" | "HIGH";
  dueDate?: string | null;
  start: string;
  end: string;
  minutes: number;
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
