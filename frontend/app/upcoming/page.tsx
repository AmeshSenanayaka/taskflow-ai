import { Suspense } from "react";
import type { Metadata } from "next";
import { PageHeader } from "@/components/layout/page-header";
import { TaskView } from "@/components/tasks/task-view";

export const metadata: Metadata = { title: "Upcoming" };

export default function UpcomingPage() {
  return (
    <>
      <PageHeader title="Upcoming" description="Open tasks due tomorrow or later." />
      <Suspense>
        <TaskView
          scope="upcoming"
          defaults={{ sortBy: "DUE_DATE", sortDirection: "ASC" }}
          emptyTitle="No upcoming tasks"
          emptyDescription="Tasks with a future due date will appear here."
        />
      </Suspense>
    </>
  );
}
