"use client";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import type { Task } from "@/types/task";

interface DeleteTaskDialogProps {
  task: Task | null;
  onConfirm: (task: Task) => void;
  onCancel: () => void;
  isPending?: boolean;
}

export function DeleteTaskDialog({ task, onConfirm, onCancel, isPending }: DeleteTaskDialogProps) {
  return (
    <AlertDialog open={task !== null} onOpenChange={(open) => (open ? undefined : onCancel())}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete this task?</AlertDialogTitle>
          <AlertDialogDescription>
            {task ? <>&ldquo;{task.title}&rdquo; will be permanently removed. This cannot be undone.</> : null}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isPending}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={isPending}
            onClick={(event) => {
              event.preventDefault();
              if (task) onConfirm(task);
            }}
          >
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
