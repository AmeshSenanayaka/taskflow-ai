import { Suspense } from "react";
import type { Metadata } from "next";
import { StatsCards } from "@/components/dashboard/stats-cards";
import { PageHeader } from "@/components/layout/page-header";
import { TaskView } from "@/components/tasks/task-view";

// The root page shares the root layout's segment, so the title template does not apply here.
export const metadata: Metadata = { title: "Dashboard · TaskFlow AI" };

export default function DashboardPage() {
  return (
    <>
      <PageHeader title="Dashboard" description="An overview of your tasks and what needs attention." />
      <div className="space-y-8">
        <StatsCards />
        <section aria-labelledby="dashboard-tasks-heading" className="space-y-4">
          <h2 id="dashboard-tasks-heading" className="text-lg font-semibold tracking-tight">
            Tasks
          </h2>
          <Suspense>
            <TaskView
              defaults={{ sortBy: "DUE_DATE", sortDirection: "ASC" }}
              emptyTitle="No tasks yet"
              emptyDescription="Create your first task to start planning your day."
            />
          </Suspense>
        </section>
      </div>
    </>
  );
}
