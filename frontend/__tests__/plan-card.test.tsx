import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PlanCard } from "@/components/assistant/plan-card";

describe("PlanCard", () => {
  it("shows the validated time blocks and the minutes used", () => {
    render(
      <PlanCard
        plan={{
          date: "2026-11-09",
          availableMinutes: 180,
          totalMinutes: 150,
          entries: [
            { taskId: "t1", title: "Fix login bug", priority: "HIGH", start: "09:00", end: "10:30", minutes: 90 },
            { taskId: "t2", title: "Prepare sprint review", priority: "MEDIUM", start: "10:45", end: "11:45", minutes: 60 },
          ],
        }}
      />,
    );

    expect(screen.getByRole("region", { name: "Plan for 2026-11-09" })).toBeInTheDocument();
    expect(screen.getByText("150 of 180 min · checked")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("Fix login bug")).toBeInTheDocument();
    expect(screen.getByText("09:00–10:30")).toBeInTheDocument();
  });
});
