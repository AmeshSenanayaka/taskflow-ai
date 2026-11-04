import { render, type RenderOptions } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";
import { AppProviders } from "@/components/providers/app-providers";
import { TooltipProvider } from "@/components/ui/tooltip";
import type { Preferences } from "@/types/preferences";
import type { Task } from "@/types/task";

export const TEST_PREFERENCES: Preferences = {
  timezone: "Asia/Colombo",
  dailyAvailableMinutes: 480,
  updatedAt: "2026-11-01T00:00:00Z",
};

export function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: overrides.id ?? crypto.randomUUID(),
    title: "Write the Phase 0 article",
    description: "Cover architecture, API and UI.",
    priority: "HIGH",
    status: "TODO",
    dueDate: "2026-11-15",
    createdAt: "2026-11-01T10:00:00Z",
    updatedAt: "2026-11-01T10:00:00Z",
    completedAt: null,
    ...overrides,
  };
}

function Providers({ children }: { children: ReactNode }) {
  return (
    <TooltipProvider>
      <AppProviders initialPreferences={TEST_PREFERENCES}>{children}</AppProviders>
    </TooltipProvider>
  );
}

export function renderWithProviders(ui: ReactElement, options?: RenderOptions) {
  return render(ui, { wrapper: Providers, ...options });
}
