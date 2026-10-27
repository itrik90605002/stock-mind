/**
 * Minimal fetch wrapper: adds the base URL, bearer token and optional Idempotency-Key, parses
 * JSON, and turns the backend's RFC 7807 problem responses into {@link ApiError}s.
 */

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? 'http://localhost:8080';
const TOKEN_KEY = 'wms.token';

/** Error thrown for any non-2xx response. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    /** Business-rule code from the backend, e.g. INSUFFICIENT_STOCK. */
    readonly code?: string,
    /** Field → message, for validation errors. */
    readonly fieldErrors?: Record<string, string>,
  ) {
    super(message);
  }
}

function safeStorage<T>(fn: () => T, fallback: T): T {
  try {
    return fn();
  } catch {
    return fallback;
  }
}

export const tokenStore = {
  get: (): string | null => safeStorage(() => localStorage.getItem(TOKEN_KEY), null),
  set: (token: string) => safeStorage(() => localStorage.setItem(TOKEN_KEY, token), undefined),
  clear: () => safeStorage(() => localStorage.removeItem(TOKEN_KEY), undefined),
};

/** Fired when the server rejects our token, so the auth context can log out. */
export const UNAUTHORIZED_EVENT = 'wms:unauthorized';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface RequestOptions {
  /** Sent as Idempotency-Key so a retried stock command is applied only once. */
  idempotencyKey?: string;
}

/** A fresh key per user action; reuse the same key when retrying that action. */
export function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export async function request<T>(method: Method, path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey;

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError('Cannot reach the server. It may be waking up — please try again in a moment.', 0);
  }

  if (response.status === 401 && token) {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(problem.detail ?? `Request failed (${response.status})`, response.status, problem.code, problem.errors);
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Builds a query string, skipping empty values. */
export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '' && value !== false) search.set(key, String(value));
  });
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}
