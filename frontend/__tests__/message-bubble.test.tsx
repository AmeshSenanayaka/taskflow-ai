import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MessageBubble } from "@/components/assistant/message-bubble";

describe("MessageBubble guardrails (Phase 3.3)", () => {
  it("says when an input guardrail answered and lists the guardrails that acted", () => {
    render(
      <MessageBubble
        message={{
          id: "m1",
          role: "assistant",
          mode: "supervisor",
          content: "I can only help with your tasks.",
          execution: { requestId: "abcdef1234", durationMs: 3, agents: [], toolCalls: [], llmCalls: 0 },
          guardrails: ["INPUT_OUT_OF_SCOPE"],
        }}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />,
    );

    expect(screen.getByText(/Answered by an input guardrail, no agent ran/)).toBeInTheDocument();
    expect(screen.getByText(/Guardrails: INPUT_OUT_OF_SCOPE/)).toBeInTheDocument();
  });
});
