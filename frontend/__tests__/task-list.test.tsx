import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TaskList } from "@/components/tasks/task-list";
import { makeTask, renderWithProviders } from "./test-utils";

const noop = () => {};
const baseProps = {
  today: "2026-11-02",
  isLoading: false,
  error: null,
  onRetry: noop,
  onEdit: noop,
  onDelete: noop,
  onStatusChange: noop,
  emptyTitle: "No tasks yet",
  emptyDescription: "Create your first task.",
};

describe("TaskList rendering", () => {
  it("renders a card per task with title, badges and due date", () => {
    const tasks = [
      makeTask({ id: "1", title: "Write article", priority: "HIGH", status: "TODO", dueDate: "2026-11-15" }),
      makeTask({ id: "2", title: "Review PR", priority: "LOW", status: "IN_PROGRESS", dueDate: null, description: null }),
    ];

    renderWithProviders(<TaskList {...baseProps} tasks={tasks} />);

    const cards = screen.getAllByTestId("task-card");
    expect(cards).toHaveLength(2);
    expect(within(cards[0]).getByRole("heading", { name: "Write article" })).toBeInTheDocument();
    expect(within(cards[0]).getByText("High priority")).toBeInTheDocument();
    expect(within(cards[0]).getByText("To do")).toBeInTheDocument();
    expect(within(cards[0]).getByText("Due Nov 15, 2026")).toBeInTheDocument();
    expect(within(cards[1]).getByText("In progress")).toBeInTheDocument();
    expect(within(cards[1]).queryByText(/Due /)).not.toBeInTheDocument();
  });

  it("highlights overdue tasks but not completed ones", () => {
    const tasks = [
      makeTask({ id: "overdue", title: "Late task", dueDate: "2026-10-30", status: "TODO" }),
      makeTask({ id: "done", title: "Finished task", dueDate: "2026-10-30", status: "COMPLETED", completedAt: "2026-10-31T00:00:00Z" }),
    ];

    renderWithProviders(<TaskList {...baseProps} tasks={tasks} />);

    const [late, finished] = screen.getAllByTestId("task-card");
    expect(late).toHaveAttribute("data-overdue", "true");
    expect(within(late).getByText("Overdue", { exact: false })).toBeInTheDocument();
    expect(finished).not.toHaveAttribute("data-overdue");
  });

  it("shows the empty state when there are no tasks", () => {
    renderWithProviders(<TaskList {...baseProps} tasks={[]} onAddTask={vi.fn()} />);

    expect(screen.getByText("No tasks yet")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /add task/i })).toBeInTheDocument();
  });

  it("shows skeletons while loading and an error with retry on failure", async () => {
    const { rerender } = renderWithProviders(<TaskList {...baseProps} tasks={null} isLoading />);
    expect(screen.getByLabelText("Loading tasks")).toBeInTheDocument();

    const onRetry = vi.fn();
    rerender(<TaskList {...baseProps} tasks={null} error={new Error("Backend down")} onRetry={onRetry} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Backend down");
    await userEvent.click(screen.getByRole("button", { name: /retry/i }));
    expect(onRetry).toHaveBeenCalled();
  });
});
