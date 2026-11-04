"use client";

import { useSyncExternalStore } from "react";
import { formatLongDate } from "@/lib/dates";

function subscribe() {
  // The date only needs to be read once per page load; nothing to subscribe to.
  return () => {};
}

/**
 * Shows today's date in the user's timezone. The value is read on the client only
 * (the server snapshot is empty), so static prerendering never bakes in a stale date.
 */
export function CurrentDate({ timezone, className }: { timezone: string; className?: string }) {
  const label = useSyncExternalStore(
    subscribe,
    () => formatLongDate(new Date(), timezone),
    () => null,
  );

  return (
    <p className={className} aria-live="off">
      {label ?? " "}
    </p>
  );
}
