import { Suspense } from "react";
import type { Metadata } from "next";
import { PageHeader } from "@/components/layout/page-header";
import { TaskView } from "@/components/tasks/task-view";

export const metadata: Metadata = { title: "Completed" };

export default function CompletedPage() {
  return (
    <>
      <PageHeader title="Completed" description="Everything you have finished. Reopen a task to bring it back." />
      <Suspense>
        <TaskView
          scope="completed"
          defaults={{ sortBy: "UPDATED_AT", sortDirection: "DESC" }}
          showStatusFilter={false}
          emptyTitle="No completed tasks yet"
          emptyDescription="Completed tasks will show up here once you mark them done."
        />
      </Suspense>
    </>
  );
}
