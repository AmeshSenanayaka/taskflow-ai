import { Badge } from "@/components/ui/badge";
import { PRIORITY_BADGE_CLASSES, PRIORITY_LABELS, STATUS_BADGE_CLASSES, STATUS_LABELS } from "@/lib/task-labels";
import { cn } from "@/lib/utils";
import type { TaskPriority, TaskStatus } from "@/types/task";

export function PriorityBadge({ priority, className }: { priority: TaskPriority; className?: string }) {
  return (
    <Badge variant="outline" className={cn(PRIORITY_BADGE_CLASSES[priority], className)}>
      {PRIORITY_LABELS[priority]} priority
    </Badge>
  );
}

export function StatusBadge({ status, className }: { status: TaskStatus; className?: string }) {
  return (
    <Badge variant="outline" className={cn(STATUS_BADGE_CLASSES[status], className)}>
      {STATUS_LABELS[status]}
    </Badge>
  );
}
