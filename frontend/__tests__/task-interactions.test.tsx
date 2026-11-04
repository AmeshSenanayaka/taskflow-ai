import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useTaskForm } from "@/components/providers/app-providers";
import { TaskCard } from "@/components/tasks/task-card";
import { TaskFormSheet } from "@/components/tasks/task-form-sheet";
import { useTaskMutations } from "@/hooks/use-task-mutations";
import { tasksApi } from "@/lib/api/tasks";
import type { Task, TaskStatus } from "@/types/task";
import { makeTask, renderWithProviders } from "./test-utils";

vi.mock("@/lib/api/tasks", () => ({
  tasksApi: {
    create: vi.fn(),
    update: vi.fn(),
    updateStatus: vi.fn(),
    remove: vi.fn(),
    list: vi.fn(),
    get: vi.fn(),
  },
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const mockedApi = vi.mocked(tasksApi);

function OpenCreateButton() {
  const { openCreate } = useTaskForm();
  return <button onClick={openCreate}>Open create</button>;
}

/** Minimal harness that wires a TaskCard to the real mutation hook (with the API mocked). */
function CardWithMutations({ task }: { task: Task }) {
  const { changeStatus } = useTaskMutations();
  return (
    <TaskCard
      task={task}
      today="2026-11-02"
      onEdit={() => {}}
      onDelete={() => {}}
      onStatusChange={(target, status: TaskStatus) => void changeStatus(target, status)}
    />
  );
}

describe("Task interactions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates a task through the form sheet and sends the right payload", async () => {
    mockedApi.create.mockResolvedValue(makeTask({ title: "Prepare demo" }));

    renderWithProviders(
      <>
        <OpenCreateButton />
        <TaskFormSheet />
      </>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Open create" }));
    expect(await screen.findByRole("heading", { name: "New task" })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("Title"), "Prepare demo");
    await userEvent.type(screen.getByLabelText("Due date"), "2026-11-10");
    await userEvent.click(screen.getByRole("button", { name: /create task/i }));

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledTimes(1));
    expect(mockedApi.create).toHaveBeenCalledWith({
      title: "Prepare demo",
      description: null,
      priority: "MEDIUM",
      dueDate: "2026-11-10",
    });
    // The sheet closes after a successful create.
    await waitFor(() => expect(screen.queryByRole("heading", { name: "New task" })).not.toBeInTheDocument());
  });

  it("keeps the sheet open and shows an error toast when the backend rejects the task", async () => {
    const { toast } = await import("sonner");
    mockedApi.create.mockRejectedValue(new Error("Backend unavailable"));

    renderWithProviders(
      <>
        <OpenCreateButton />
        <TaskFormSheet />
      </>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Open create" }));
    await userEvent.type(await screen.findByLabelText("Title"), "Will fail");
    await userEvent.click(screen.getByRole("button", { name: /create task/i }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Backend unavailable"));
    expect(screen.getByRole("heading", { name: "New task" })).toBeInTheDocument();
  });

  it("marks a task as completed through the status endpoint", async () => {
    const task = makeTask({ id: "task-1", status: "TODO" });
    mockedApi.updateStatus.mockResolvedValue({ ...task, status: "COMPLETED", completedAt: "2026-11-02T10:00:00Z" });

    renderWithProviders(<CardWithMutations task={task} />);

    await userEvent.click(screen.getByRole("button", { name: "Complete task" }));

    await waitFor(() => expect(mockedApi.updateStatus).toHaveBeenCalledWith("task-1", "COMPLETED"));
  });

  it("reopens a completed task", async () => {
    const task = makeTask({ id: "task-2", status: "COMPLETED", completedAt: "2026-11-01T10:00:00Z" });
    mockedApi.updateStatus.mockResolvedValue({ ...task, status: "TODO", completedAt: null });

    renderWithProviders(<CardWithMutations task={task} />);

    await userEvent.click(screen.getByRole("button", { name: "Reopen task" }));

    await waitFor(() => expect(mockedApi.updateStatus).toHaveBeenCalledWith("task-2", "TODO"));
  });
});
