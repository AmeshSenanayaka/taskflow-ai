import { describe, expect, it } from "vitest";
import { addDays, getDueState, todayInTimezone } from "@/lib/dates";

describe("date helpers", () => {
  it("resolves today in the configured timezone", () => {
    const instant = new Date("2026-11-02T22:30:00Z");
    expect(todayInTimezone("Asia/Colombo", instant)).toBe("2026-11-03");
    expect(todayInTimezone("America/New_York", instant)).toBe("2026-11-02");
  });

  it("adds days across month boundaries", () => {
    expect(addDays("2026-11-30", 1)).toBe("2026-12-01");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
  });

  it("classifies due dates relative to today", () => {
    const today = "2026-11-02";
    expect(getDueState("2026-11-01", "TODO", today)).toBe("overdue");
    expect(getDueState("2026-11-01", "COMPLETED", today)).toBe("past");
    expect(getDueState("2026-11-02", "TODO", today)).toBe("today");
    expect(getDueState("2026-11-03", "TODO", today)).toBe("tomorrow");
    expect(getDueState("2026-11-20", "TODO", today)).toBe("upcoming");
    expect(getDueState(null, "TODO", today)).toBe("none");
  });
});
