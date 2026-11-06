import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AssistantChat } from "@/components/assistant/assistant-chat";
import { useDataRefresh } from "@/components/providers/app-providers";
import { ApiError } from "@/lib/api/client";
import { aiApi } from "@/lib/api/ai";
import type { ChatResponse } from "@/types/ai";
import { renderWithProviders } from "./test-utils";

vi.mock("@/lib/api/ai", () => ({
  aiApi: {
    status: vi.fn(),
    startConversation: vi.fn(),
    chat: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
}));

const mockedApi = vi.mocked(aiApi);

function reply(overrides: Partial<ChatResponse> = {}): ChatResponse {
  return {
    conversationId: "conv-1234567",
    message: "Done.",
    status: "COMPLETED",
    pendingConfirmation: null,
    dataChanged: false,
    execution: { requestId: "req-1", durationMs: 42, toolCalls: [], tokenUsage: { inputTokens: 10, outputTokens: 5, totalTokens: 15 } },
    ...overrides,
  };
}

/** Exposes the shared refresh counter so tests can assert that task views would re-fetch. */
function VersionProbe() {
  const { version } = useDataRefresh();
  return <span data-testid="version">{version}</span>;
}

async function typeAndSend(text: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Message the assistant"), text);
  await user.keyboard("{Enter}");
  return user;
}

describe("AssistantChat", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.sessionStorage.clear();
    mockedApi.status.mockResolvedValue({ available: true, model: "gpt-4o-mini" });
  });

  it("renders user and assistant messages and shows a loading indicator while waiting", async () => {
    let resolve: (value: ChatResponse) => void = () => {};
    mockedApi.chat.mockReturnValue(new Promise<ChatResponse>((r) => (resolve = r)));
    renderWithProviders(<AssistantChat />);

    await typeAndSend("Show my pending tasks");

    expect(screen.getByTestId("message-user")).toHaveTextContent("Show my pending tasks");
    expect(screen.getByRole("status")).toHaveTextContent("Thinking");
    expect(mockedApi.chat).toHaveBeenCalledWith({ conversationId: null, message: "Show my pending tasks" });

    resolve(reply({ message: "You have 3 pending tasks." }));
    await waitFor(() => expect(screen.getByTestId("message-assistant")).toHaveTextContent("You have 3 pending tasks."));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("continues the conversation with the id returned by the backend", async () => {
    mockedApi.chat.mockResolvedValue(reply());
    renderWithProviders(<AssistantChat />);

    const user = await typeAndSend("Create a task to review the API documentation");
    await waitFor(() => expect(screen.getByTestId("message-assistant")).toBeInTheDocument());
    await user.type(screen.getByLabelText("Message the assistant"), "Change its priority to HIGH{Enter}");

    await waitFor(() => expect(mockedApi.chat).toHaveBeenCalledTimes(2));
    expect(mockedApi.chat).toHaveBeenLastCalledWith({ conversationId: "conv-1234567", message: "Change its priority to HIGH" });
  });

  it("shows an error state with retry when the request fails", async () => {
    mockedApi.chat.mockRejectedValueOnce(new ApiError(503, { title: "AI service error", detail: "The AI assistant is currently unavailable." }));
    mockedApi.chat.mockResolvedValueOnce(reply({ message: "Back online." }));
    renderWithProviders(<AssistantChat />);

    const user = await typeAndSend("Hello");

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("The AI assistant is currently unavailable.");
    expect(screen.getByText("Not sent")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(screen.getByTestId("message-assistant")).toHaveTextContent("Back online."));
    expect(mockedApi.chat).toHaveBeenCalledTimes(2);
  });

  it("refreshes task data after a response that changed data", async () => {
    mockedApi.chat.mockResolvedValue(
      reply({
        message: "Created it.",
        dataChanged: true,
        execution: { requestId: "req-2", durationMs: 100, toolCalls: [{ name: "createTask", durationMs: 12, success: true }], tokenUsage: null },
      }),
    );
    renderWithProviders(
      <>
        <VersionProbe />
        <AssistantChat />
      </>,
    );
    expect(screen.getByTestId("version")).toHaveTextContent("0");

    await typeAndSend("Create a high-priority task for tomorrow");

    await waitFor(() => expect(screen.getByTestId("version")).toHaveTextContent("1"));
    expect(screen.getByText("Execution details")).toBeInTheDocument();
  });

  it("approves a pending deletion through the backend confirmation endpoint and refreshes data", async () => {
    mockedApi.chat.mockResolvedValue(
      reply({
        message: "Deleting “Obsolete” needs your approval.",
        status: "PENDING_CONFIRMATION",
        pendingConfirmation: {
          confirmationId: "conf-1234567",
          operation: "DELETE_TASK",
          taskId: "11111111-1111-1111-1111-111111111111",
          taskTitle: "Obsolete",
          expiresAt: "2026-11-04T10:05:00Z",
        },
      }),
    );
    mockedApi.approve.mockResolvedValue(reply({ message: "Deleted “Obsolete”.", dataChanged: true }));
    renderWithProviders(
      <>
        <VersionProbe />
        <AssistantChat />
      </>,
    );

    const user = await typeAndSend("Delete the obsolete task");
    const card = await screen.findByTestId("confirmation-card");
    expect(card).toHaveTextContent("Delete this task?");
    expect(card).toHaveTextContent("Obsolete");
    expect(screen.getByTestId("version")).toHaveTextContent("0");

    await user.click(screen.getByRole("button", { name: "Approve deletion" }));

    await waitFor(() => expect(mockedApi.approve).toHaveBeenCalledWith("conf-1234567", "conv-1234567"));
    await waitFor(() => expect(screen.getByTestId("version")).toHaveTextContent("1"));
    expect(screen.getByText("Deleted")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve deletion" })).not.toBeInTheDocument();
    expect(screen.getAllByTestId("message-assistant").at(-1)).toHaveTextContent("Deleted “Obsolete”.");
  });

  it("rejects a pending deletion without touching task data", async () => {
    mockedApi.chat.mockResolvedValue(
      reply({
        message: "Please approve the deletion.",
        status: "PENDING_CONFIRMATION",
        pendingConfirmation: {
          confirmationId: "conf-1234567",
          operation: "DELETE_TASK",
          taskId: "11111111-1111-1111-1111-111111111111",
          taskTitle: "Keep me",
          expiresAt: "2026-11-04T10:05:00Z",
        },
      }),
    );
    mockedApi.reject.mockResolvedValue(reply({ message: "Okay, I have not deleted “Keep me”." }));
    renderWithProviders(
      <>
        <VersionProbe />
        <AssistantChat />
      </>,
    );

    const user = await typeAndSend("Delete keep me");
    await screen.findByTestId("confirmation-card");
    await user.click(screen.getByRole("button", { name: "Keep task" }));

    await waitFor(() => expect(mockedApi.reject).toHaveBeenCalledWith("conf-1234567", "conv-1234567"));
    expect(mockedApi.approve).not.toHaveBeenCalled();
    expect(screen.getByTestId("version")).toHaveTextContent("0");
    await waitFor(() => expect(screen.getByTestId("confirmation-card")).toHaveTextContent("Kept"));
  });

  it("explains when the assistant is not configured and disables input", async () => {
    mockedApi.status.mockResolvedValue({ available: false, message: "The AI assistant is not configured on the backend." });
    renderWithProviders(<AssistantChat />);

    expect(await screen.findByRole("alert")).toHaveTextContent("not configured");
    expect(screen.getByLabelText("Message the assistant")).toBeDisabled();
  });
});
