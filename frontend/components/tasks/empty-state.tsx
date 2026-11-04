import { ClipboardList, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";

interface EmptyStateProps {
  title: string;
  description: string;
  onAddTask?: () => void;
}

export function EmptyState({ title, description, onAddTask }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed bg-background px-6 py-14 text-center">
      <span className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <ClipboardList className="size-6" aria-hidden="true" />
      </span>
      <h3 className="mt-4 text-base font-medium">{title}</h3>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>
      {onAddTask ? (
        <Button className="mt-5" onClick={onAddTask}>
          <Plus data-icon="inline-start" />
          Add task
        </Button>
      ) : null}
    </div>
  );
}
