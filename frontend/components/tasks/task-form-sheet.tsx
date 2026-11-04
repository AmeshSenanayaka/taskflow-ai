"use client";

import { useTaskForm } from "@/components/providers/app-providers";
import { TaskForm } from "@/components/tasks/task-form";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { useTaskMutations } from "@/hooks/use-task-mutations";
import type { TaskFormValues } from "@/lib/validations/task";
import type { Task } from "@/types/task";

function toFormValues(task: Task): Partial<TaskFormValues> {
  return {
    title: task.title,
    description: task.description ?? "",
    priority: task.priority,
    status: task.status,
    dueDate: task.dueDate ?? "",
  };
}

/**
 * Side drawer used for both creating and editing tasks. Rendered once in the app
 * shell; opened through the TaskForm context from anywhere in the UI.
 */
export function TaskFormSheet() {
  const { state, close } = useTaskForm();
  const { createTask, updateTask, isPending } = useTaskMutations();

  const editingTask = state.open ? state.task : null;
  const mode = editingTask ? "edit" : "create";

  async function handleSubmit(values: TaskFormValues) {
    const description = values.description.trim() === "" ? null : values.description.trim();
    const dueDate = values.dueDate === "" ? null : values.dueDate;

    const result = editingTask
      ? await updateTask(editingTask.id, { title: values.title, description, priority: values.priority, status: values.status, dueDate })
      : await createTask({ title: values.title, description, priority: values.priority, dueDate });

    if (result) close();
  }

  return (
    <Sheet open={state.open} onOpenChange={(open) => (open ? undefined : close())}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-lg">
        <SheetHeader className="border-b px-6 py-5">
          <SheetTitle>{mode === "create" ? "New task" : "Edit task"}</SheetTitle>
          <SheetDescription>
            {mode === "create" ? "Add a task to your list. You can change everything later." : "Update the details, priority, status or due date."}
          </SheetDescription>
        </SheetHeader>
        <div className="flex flex-1 flex-col overflow-y-auto px-6 py-5">
          {state.open ? (
            <TaskForm
              key={editingTask?.id ?? "new"}
              mode={mode}
              defaultValues={editingTask ? toFormValues(editingTask) : undefined}
              onSubmit={handleSubmit}
              onCancel={close}
              isSubmitting={isPending}
            />
          ) : null}
        </div>
      </SheetContent>
    </Sheet>
  );
}
