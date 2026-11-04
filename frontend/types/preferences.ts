/** Matches PreferencesResponse. */
export interface Preferences {
  timezone: string;
  dailyAvailableMinutes: number;
  updatedAt: string;
}

/** Matches UpdatePreferencesRequest. */
export interface UpdatePreferencesRequest {
  timezone: string;
  dailyAvailableMinutes: number;
}
