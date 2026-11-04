"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { preferencesApi } from "@/lib/api/preferences";
import type { Preferences } from "@/types/preferences";
import type { Task } from "@/types/task";

/* ------------------------------------------------------------------ */
/* Data refresh: a counter every data hook subscribes to. Bumping it   */
/* after a mutation re-fetches task lists and dashboard statistics.    */
/* ------------------------------------------------------------------ */

interface DataRefreshContextValue {
  version: number;
  invalidate: () => void;
}

const DataRefreshContext = createContext<DataRefreshContextValue | null>(null);

export function useDataRefresh(): DataRefreshContextValue {
  const context = useContext(DataRefreshContext);
  if (!context) throw new Error("useDataRefresh must be used within AppProviders");
  return context;
}

/* ------------------------------------------------------------------ */
/* Preferences: loaded once, shared so every date calculation uses the */
/* configured timezone.                                                */
/* ------------------------------------------------------------------ */

interface PreferencesContextValue {
  preferences: Preferences | null;
  timezone: string;
  setPreferences: (preferences: Preferences) => void;
}

const PreferencesContext = createContext<PreferencesContextValue | null>(null);

export function useUserPreferences(): PreferencesContextValue {
  const context = useContext(PreferencesContext);
  if (!context) throw new Error("useUserPreferences must be used within AppProviders");
  return context;
}

/* ------------------------------------------------------------------ */
/* Task form: the create/edit sheet is rendered once at the app shell  */
/* level, so any component (e.g. the top bar) can open it.             */
/* ------------------------------------------------------------------ */

export type TaskFormState = { open: false } | { open: true; task: Task | null };

interface TaskFormContextValue {
  state: TaskFormState;
  openCreate: () => void;
  openEdit: (task: Task) => void;
  close: () => void;
}

const TaskFormContext = createContext<TaskFormContextValue | null>(null);

export function useTaskForm(): TaskFormContextValue {
  const context = useContext(TaskFormContext);
  if (!context) throw new Error("useTaskForm must be used within AppProviders");
  return context;
}

/* ------------------------------------------------------------------ */

function browserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

export function AppProviders({ children, initialPreferences = null }: { children: ReactNode; initialPreferences?: Preferences | null }) {
  const [version, setVersion] = useState(0);
  const invalidate = useCallback(() => setVersion((current) => current + 1), []);

  const [preferences, setPreferences] = useState<Preferences | null>(initialPreferences);
  useEffect(() => {
    if (initialPreferences) return;
    const controller = new AbortController();
    preferencesApi
      .get(controller.signal)
      .then(setPreferences)
      .catch(() => {
        /* The backend may be down; pages surface their own error states. */
      });
    return () => controller.abort();
  }, [initialPreferences]);

  const [formState, setFormState] = useState<TaskFormState>({ open: false });

  const refreshValue = useMemo(() => ({ version, invalidate }), [version, invalidate]);
  const preferencesValue = useMemo(
    () => ({ preferences, timezone: preferences?.timezone ?? browserTimezone(), setPreferences }),
    [preferences],
  );
  const formValue = useMemo<TaskFormContextValue>(
    () => ({
      state: formState,
      openCreate: () => setFormState({ open: true, task: null }),
      openEdit: (task) => setFormState({ open: true, task }),
      close: () => setFormState({ open: false }),
    }),
    [formState],
  );

  return (
    <DataRefreshContext.Provider value={refreshValue}>
      <PreferencesContext.Provider value={preferencesValue}>
        <TaskFormContext.Provider value={formValue}>{children}</TaskFormContext.Provider>
      </PreferencesContext.Provider>
    </DataRefreshContext.Provider>
  );
}
