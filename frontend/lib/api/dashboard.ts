import { apiRequest } from "@/lib/api/client";
import type { DashboardStats } from "@/types/dashboard";

export const dashboardApi = {
  stats(signal?: AbortSignal): Promise<DashboardStats> {
    return apiRequest<DashboardStats>("/api/dashboard/stats", { signal });
  },
};
