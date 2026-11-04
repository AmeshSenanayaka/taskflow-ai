import { Suspense } from "react";
import type { Metadata } from "next";
import { PageHeader } from "@/components/layout/page-header";
import { TaskView } from "@/components/tasks/task-view";

export const metadata: Metadata = { title: "Today" };

export default function TodayPage() {
  return (
    <>
      <PageHeader title="Today" description="Open tasks due today in your configured timezone." />
      <Suspense>
        <TaskView
          scope="today"
          defaults={{ sortBy: "PRIORITY", sortDirection: "DESC" }}
          emptyTitle="Nothing due today"
          emptyDescription="Enjoy the breathing room, or pull something forward from Upcoming."
        />
      </Suspense>
    </>
  );
}
