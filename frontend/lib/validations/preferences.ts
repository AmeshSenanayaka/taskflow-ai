import { z } from "zod";

export function isValidTimezone(value: string): boolean {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: value });
    return true;
  } catch {
    return false;
  }
}

export const preferencesFormSchema = z.object({
  timezone: z
    .string()
    .trim()
    .min(1, "Timezone is required")
    .refine(isValidTimezone, "Enter a valid IANA timezone such as Asia/Colombo"),
  dailyAvailableMinutes: z
    .number({ message: "Enter a number of minutes" })
    .int("Minutes must be a whole number")
    .min(0, "Minutes must be at least 0")
    .max(1440, "Minutes must be at most 1440 (24 hours)"),
});

export type PreferencesFormValues = z.infer<typeof preferencesFormSchema>;
