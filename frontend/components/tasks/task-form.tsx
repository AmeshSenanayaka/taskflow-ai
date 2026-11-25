"use client";

import { useId } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { PRIORITY_LABELS, STATUS_LABELS } from "@/lib/task-labels";
import { defaultTaskFormValues, taskFormSchema, type TaskFormValues } from "@/lib/validations/task";
import { TASK_PRIORITIES, TASK_STATUSES } from "@/types/task";

/** Server messages per field, e.g. a business rule refusing a past due date (422). */
export type TaskFormServerErrors = Record<string, string>;

const FORM_FIELDS: ReadonlyArray<keyof TaskFormValues> = [
  "title",
  "description",
  "priority",
  "status",
  "dueDate",
  "estimatedMinutes",
];

export interface TaskFormProps {
  mode: "create" | "edit";
  defaultValues?: Partial<TaskFormValues>;
  /** May resolve to the server's field errors; they are shown on the matching fields. */
  onSubmit: (values: TaskFormValues) => Promise<TaskFormServerErrors | void> | TaskFormServerErrors | void;
  onCancel: () => void;
  isSubmitting?: boolean;
}

function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) return null;
  return (
    <p id={id} role="alert" className="text-sm text-destructive">
      {message}
    </p>
  );
}

export function TaskForm({ mode, defaultValues, onSubmit, onCancel, isSubmitting = false }: TaskFormProps) {
  const ids = {
    title: useId(),
    description: useId(),
    priority: useId(),
    status: useId(),
    dueDate: useId(),
    estimatedMinutes: useId(),
  };

  const form = useForm<TaskFormValues>({
    resolver: zodResolver(taskFormSchema),
    defaultValues: { ...defaultTaskFormValues, ...defaultValues },
    mode: "onSubmit",
    reValidateMode: "onChange",
  });
  const { errors } = form.formState;
  const description = useWatch({ control: form.control, name: "description" }) ?? "";

  async function submit(values: TaskFormValues) {
    const serverErrors = await onSubmit(values);
    if (!serverErrors) return;
    for (const [field, message] of Object.entries(serverErrors)) {
      const name = FORM_FIELDS.find((candidate) => candidate === field);
      if (name) form.setError(name, { type: "server", message }, { shouldFocus: true });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(submit)} className="flex flex-1 flex-col gap-5" noValidate>
      <div className="space-y-2">
        <Label htmlFor={ids.title}>Title</Label>
        <Input
          id={ids.title}
          placeholder="What needs to be done?"
          autoFocus
          maxLength={150}
          aria-invalid={Boolean(errors.title)}
          aria-describedby={errors.title ? `${ids.title}-error` : undefined}
          {...form.register("title")}
        />
        <FieldError id={`${ids.title}-error`} message={errors.title?.message} />
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <Label htmlFor={ids.description}>Description</Label>
          <span className="text-xs text-muted-foreground tabular-nums">{description.length}/2000</span>
        </div>
        <Textarea
          id={ids.description}
          placeholder="Add details, links or notes (optional)"
          rows={5}
          maxLength={2000}
          aria-invalid={Boolean(errors.description)}
          aria-describedby={errors.description ? `${ids.description}-error` : undefined}
          {...form.register("description")}
        />
        <FieldError id={`${ids.description}-error`} message={errors.description?.message} />
      </div>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor={ids.priority}>Priority</Label>
          <Controller
            control={form.control}
            name="priority"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id={ids.priority} className="w-full" aria-invalid={Boolean(errors.priority)}>
                  <SelectValue placeholder="Choose a priority" />
                </SelectTrigger>
                <SelectContent>
                  {TASK_PRIORITIES.map((priority) => (
                    <SelectItem key={priority} value={priority}>
                      {PRIORITY_LABELS[priority]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          <FieldError id={`${ids.priority}-error`} message={errors.priority?.message} />
        </div>

        {mode === "edit" ? (
          <div className="space-y-2">
            <Label htmlFor={ids.status}>Status</Label>
            <Controller
              control={form.control}
              name="status"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id={ids.status} className="w-full" aria-invalid={Boolean(errors.status)}>
                    <SelectValue placeholder="Choose a status" />
                  </SelectTrigger>
                  <SelectContent>
                    {TASK_STATUSES.map((status) => (
                      <SelectItem key={status} value={status}>
                        {STATUS_LABELS[status]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            <FieldError id={`${ids.status}-error`} message={errors.status?.message} />
          </div>
        ) : null}

        <div className="space-y-2">
          <Label htmlFor={ids.dueDate}>Due date</Label>
          <Input
            id={ids.dueDate}
            type="date"
            aria-invalid={Boolean(errors.dueDate)}
            aria-describedby={errors.dueDate ? `${ids.dueDate}-error` : undefined}
            {...form.register("dueDate")}
          />
          <FieldError id={`${ids.dueDate}-error`} message={errors.dueDate?.message} />
        </div>

        <div className="space-y-2">
          <Label htmlFor={ids.estimatedMinutes}>Estimate (minutes)</Label>
          <Input
            id={ids.estimatedMinutes}
            type="number"
            inputMode="numeric"
            min={1}
            max={1440}
            placeholder="Optional, e.g. 45"
            aria-invalid={Boolean(errors.estimatedMinutes)}
            aria-describedby={errors.estimatedMinutes ? `${ids.estimatedMinutes}-error` : undefined}
            {...form.register("estimatedMinutes")}
          />
          <FieldError id={`${ids.estimatedMinutes}-error`} message={errors.estimatedMinutes?.message} />
        </div>
      </div>

      <div className="mt-auto flex items-center justify-end gap-2 border-t pt-4">
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? <Loader2 className="animate-spin" data-icon="inline-start" /> : null}
          {mode === "create" ? "Create task" : "Save changes"}
        </Button>
      </div>
    </form>
  );
}
