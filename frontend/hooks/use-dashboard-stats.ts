"use client";

import { useApiQuery } from "@/hooks/use-api-query";
import { dashboardApi } from "@/lib/api/dashboard";
import type { DashboardStats } from "@/types/dashboard";

export function useDashboardStats() {
  return useApiQuery<DashboardStats>((signal) => dashboardApi.stats(signal), "dashboard-stats");
}
