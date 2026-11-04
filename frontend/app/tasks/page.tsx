import { Suspense } from "react";
import type { Metadata } from "next";
import { PageHeader } from "@/components/layout/page-header";
import { TaskView } from "@/components/tasks/task-view";

export const metadata: Metadata = { title: "All Tasks" };

export default function AllTasksPage() {
  return (
    <>
      <PageHeader title="All Tasks" description="Every task, with filters, sorting and pagination." />
      <Suspense>
        <TaskView emptyTitle="No tasks found" emptyDescription="Try clearing the filters, or add a new task." />
      </Suspense>
    </>
  );
}
