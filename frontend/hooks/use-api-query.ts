"use client";

import { useCallback, useEffect, useState } from "react";
import { useDataRefresh } from "@/components/providers/app-providers";

export interface ApiQueryState<T> {
  data: T | null;
  error: unknown;
  isLoading: boolean;
  refetch: () => void;
}

interface Result<T> {
  key: string;
  data: T | null;
  error: unknown;
}

/**
 * Minimal data-fetching hook: runs `fetcher` whenever `key` changes or when any
 * mutation calls `invalidate()`. In-flight requests are aborted on re-run/unmount.
 * Previous data is kept while a new request is loading, which avoids flicker when
 * paginating. Deliberately simple: Phase 0 does not need a caching library.
 */
export function useApiQuery<T>(fetcher: (signal: AbortSignal) => Promise<T>, key: string): ApiQueryState<T> {
  const { version } = useDataRefresh();
  const [localVersion, setLocalVersion] = useState(0);
  const requestKey = `${key}|${version}|${localVersion}`;
  const [result, setResult] = useState<Result<T>>({ key: "", data: null, error: null });

  useEffect(() => {
    const controller = new AbortController();
    fetcher(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, data, error: null });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setResult((previous) => ({ key: requestKey, data: previous.data, error }));
      });
    return () => controller.abort();
    // `requestKey` encodes every input that should trigger a refetch; the fetcher
    // closure is recreated on each render but always reads the same inputs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey]);

  const refetch = useCallback(() => setLocalVersion((current) => current + 1), []);

  return {
    data: result.data,
    error: result.key === requestKey ? result.error : null,
    isLoading: result.key !== requestKey,
    refetch,
  };
}
