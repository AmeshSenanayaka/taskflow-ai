import type { ProblemDetail } from "@/types/api";

/**
 * Base URL of the Spring Boot backend. Configured through NEXT_PUBLIC_API_BASE_URL
 * (see .env.example); defaults to the local backend.
 */
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");

/** Thrown for every non-2xx response. Carries the backend's Problem Details. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail?: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, problem?: Partial<ProblemDetail>, fallbackMessage?: string) {
    const detail = problem?.detail ?? fallbackMessage ?? `Request failed with status ${status}`;
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.title = problem?.title ?? "Request failed";
    this.detail = problem?.detail;
    this.fieldErrors = problem?.errors ?? {};
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isValidationError(): boolean {
    return this.status === 400;
  }
}

type QueryValue = string | number | boolean | undefined | null | Array<string | number>;

/** Builds a query string, skipping empty values and repeating array parameters. */
export function buildQueryString(params: Record<string, QueryValue>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      if (value.length === 0) continue;
      value.forEach((item) => search.append(key, String(item)));
    } else {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
}

/**
 * Single entry point for all HTTP calls to the backend.
 * - Serialises the body as JSON
 * - Parses JSON responses (204 No Content resolves to undefined)
 * - Converts error responses into ApiError instances
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, signal } = options;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
      cache: "no-store",
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new ApiError(0, undefined, "Cannot reach the TaskFlow backend. Is it running?");
  }

  if (!response.ok) {
    let problem: Partial<ProblemDetail> | undefined;
    try {
      problem = (await response.json()) as Partial<ProblemDetail>;
    } catch {
      problem = undefined;
    }
    throw new ApiError(response.status, problem, response.statusText);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Human-friendly message for any error thrown by the API layer. */
export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = Object.values(error.fieldErrors);
    if (fields.length > 0) return fields.join(". ");
    return error.detail ?? error.title;
  }
  if (error instanceof Error) return error.message;
  return "Something went wrong";
}
