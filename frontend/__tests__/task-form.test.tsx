import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TaskForm } from "@/components/tasks/task-form";
import { renderWithProviders } from "./test-utils";

describe("TaskForm validation", () => {
  it("shows a validation message when the title is blank", async () => {
    const onSubmit = vi.fn();
    renderWithProviders(<TaskForm mode="create" onSubmit={onSubmit} onCancel={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: /create task/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Title is required");
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("rejects titles longer than 150 characters", async () => {
    const onSubmit = vi.fn();
    renderWithProviders(<TaskForm mode="create" onSubmit={onSubmit} onCancel={vi.fn()} />);

    const title = screen.getByLabelText("Title");
    // maxLength on the input blocks typing beyond 150, so set the value directly.
    await userEvent.click(title);
    (title as HTMLInputElement).maxLength = 500;
    await userEvent.paste("x".repeat(151));
    await userEvent.click(screen.getByRole("button", { name: /create task/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Title must be at most 150 characters");
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("submits trimmed values with the default MEDIUM priority", async () => {
    const onSubmit = vi.fn();
    renderWithProviders(<TaskForm mode="create" onSubmit={onSubmit} onCancel={vi.fn()} />);

    await userEvent.type(screen.getByLabelText("Title"), "  Ship Phase 0  ");
    await userEvent.type(screen.getByLabelText("Description"), "Write the article");
    await userEvent.type(screen.getByLabelText("Due date"), "2026-11-20");
    await userEvent.click(screen.getByRole("button", { name: /create task/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith({
      title: "Ship Phase 0",
      description: "Write the article",
      priority: "MEDIUM",
      status: "TODO",
      dueDate: "2026-11-20",
    });
  });

  it("only shows the status field in edit mode", () => {
    const { unmount } = renderWithProviders(<TaskForm mode="create" onSubmit={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.queryByLabelText("Status")).not.toBeInTheDocument();
    unmount();

    renderWithProviders(<TaskForm mode="edit" defaultValues={{ status: "IN_PROGRESS" }} onSubmit={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.getByLabelText("Status")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save changes/i })).toBeInTheDocument();
  });
});
