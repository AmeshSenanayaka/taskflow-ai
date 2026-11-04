/** Matches PageResponse<T>. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** RFC 9457 Problem Details as produced by the backend's GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  /** Field-level validation errors (field name -> message). */
  errors?: Record<string, string>;
}
