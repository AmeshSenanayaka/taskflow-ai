import { apiRequest } from "@/lib/api/client";
import type { Preferences, UpdatePreferencesRequest } from "@/types/preferences";

export const preferencesApi = {
  get(signal?: AbortSignal): Promise<Preferences> {
    return apiRequest<Preferences>("/api/preferences", { signal });
  },

  update(request: UpdatePreferencesRequest): Promise<Preferences> {
    return apiRequest<Preferences>("/api/preferences", { method: "PUT", body: request });
  },
};
