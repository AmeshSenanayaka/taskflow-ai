"use client";

import { useId, useMemo, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useDataRefresh, useUserPreferences } from "@/components/providers/app-providers";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { getErrorMessage } from "@/lib/api/client";
import { preferencesApi } from "@/lib/api/preferences";
import { todayInTimezone } from "@/lib/dates";
import { preferencesFormSchema, type PreferencesFormValues } from "@/lib/validations/preferences";

function supportedTimezones(): string[] {
  try {
    return Intl.supportedValuesOf("timeZone");
  } catch {
    return [];
  }
}

export function PreferencesForm() {
  const { preferences, setPreferences } = useUserPreferences();
  const { invalidate } = useDataRefresh();
  const [isSaving, setIsSaving] = useState(false);
  const timezoneListId = useId();
  const ids = { timezone: useId(), minutes: useId() };
  const timezones = useMemo(() => supportedTimezones(), []);

  const form = useForm<PreferencesFormValues>({
    resolver: zodResolver(preferencesFormSchema),
    // `values` keeps the form in sync once the preferences have loaded.
    values: preferences ? { timezone: preferences.timezone, dailyAvailableMinutes: preferences.dailyAvailableMinutes } : undefined,
    defaultValues: { timezone: "Asia/Colombo", dailyAvailableMinutes: 480 },
  });
  const { errors, isDirty } = form.formState;
  const watchedTimezone = useWatch({ control: form.control, name: "timezone" });
  const watchedMinutes = Number(useWatch({ control: form.control, name: "dailyAvailableMinutes" }));

  async function onSubmit(values: PreferencesFormValues) {
    setIsSaving(true);
    try {
      const updated = await preferencesApi.update(values);
      setPreferences(updated);
      invalidate(); // dashboard "today" depends on the timezone
      toast.success("Preferences saved");
      form.reset({ timezone: updated.timezone, dailyAvailableMinutes: updated.dailyAvailableMinutes });
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  if (!preferences) {
    return (
      <Card className="rounded-xl shadow-sm">
        <CardContent className="space-y-4 p-6">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-9 w-full" />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="rounded-xl shadow-sm">
      <CardHeader>
        <CardTitle>Preferences</CardTitle>
        <CardDescription>These settings drive date calculations today and daily planning in later phases.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6" noValidate>
          <div className="space-y-2">
            <Label htmlFor={ids.timezone}>Timezone</Label>
            <Input
              id={ids.timezone}
              list={timezoneListId}
              placeholder="Asia/Colombo"
              autoComplete="off"
              aria-invalid={Boolean(errors.timezone)}
              aria-describedby={`${ids.timezone}-hint`}
              {...form.register("timezone")}
            />
            <datalist id={timezoneListId}>
              {timezones.map((zone) => (
                <option key={zone} value={zone} />
              ))}
            </datalist>
            {errors.timezone ? (
              <p role="alert" className="text-sm text-destructive">
                {errors.timezone.message}
              </p>
            ) : (
              <p id={`${ids.timezone}-hint`} className="text-xs text-muted-foreground">
                IANA identifier. Today in this timezone: {watchedTimezone && !errors.timezone ? todayInTimezone(watchedTimezone) : "—"}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor={ids.minutes}>Daily available minutes</Label>
            <Input
              id={ids.minutes}
              type="number"
              min={0}
              max={1440}
              step={15}
              className="max-w-xs"
              aria-invalid={Boolean(errors.dailyAvailableMinutes)}
              {...form.register("dailyAvailableMinutes", { valueAsNumber: true })}
            />
            {errors.dailyAvailableMinutes ? (
              <p role="alert" className="text-sm text-destructive">
                {errors.dailyAvailableMinutes.message}
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">
                {Number.isFinite(watchedMinutes) ? `${(watchedMinutes / 60).toFixed(1)} hours per day` : "Minutes you can dedicate to tasks each day"}
              </p>
            )}
          </div>

          <div className="flex items-center gap-2 border-t pt-4">
            <Button type="submit" disabled={isSaving || !isDirty}>
              {isSaving ? <Loader2 className="animate-spin" data-icon="inline-start" /> : null}
              Save preferences
            </Button>
            <Button type="button" variant="ghost" disabled={isSaving || !isDirty} onClick={() => form.reset()}>
              Reset
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
