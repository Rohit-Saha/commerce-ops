import { loadSession, SECURITY_MODE } from "../lib/authSession";

export const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

type UnauthorizedHandler = () => void;

let onUnauthorized: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler;
}

async function authHeaders(): Promise<Record<string, string>> {
  if (SECURITY_MODE === "oidc") {
    // Session cookie (credentials: include) authenticates; no Authorization header.
    return {};
  }
  const apiKey = loadSession()?.apiKey ?? "";
  return apiKey ? { "X-API-Key": apiKey } : {};
}

interface ApiEnvelope<T> {
  success?: boolean;
  message?: string;
  data?: T;
}

export function unwrapData<T>(parsed: unknown): T {
  if (
    parsed !== null &&
    typeof parsed === "object" &&
    "success" in parsed &&
    (parsed as ApiEnvelope<T>).success === true &&
    "data" in parsed
  ) {
    return (parsed as ApiEnvelope<T>).data as T;
  }
  return parsed as T;
}

function errorMessage(parsedBody: unknown, fallback: string): string {
  if (parsedBody !== null && typeof parsedBody === "object" && "message" in parsedBody) {
    const message = (parsedBody as { message?: unknown }).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }
  return fallback;
}

interface RequestOptions extends Omit<RequestInit, "headers" | "body"> {
  headers?: Record<string, string>;
  body?: unknown;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { headers, body, ...rest } = options;
  const auth = await authHeaders();

  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    credentials: SECURITY_MODE === "oidc" ? "include" : "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...auth,
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!res.ok) {
    let parsedBody: unknown;
    try {
      parsedBody = await res.json();
    } catch {
      // no JSON
    }
    if (res.status === 401) {
      onUnauthorized?.();
    }
    throw new ApiError(
      res.status,
      errorMessage(parsedBody, `Request failed: ${res.status} ${res.statusText}`),
      parsedBody,
    );
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  if (!text) {
    return undefined as T;
  }
  return unwrapData<T>(JSON.parse(text));
}

export const api = {
  get: <T>(path: string, headers?: Record<string, string>) => request<T>(path, { method: "GET", headers }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, { method: "POST", body, headers }),
  put: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, { method: "PUT", body, headers }),
  delete: <T>(path: string, headers?: Record<string, string>) =>
    request<T>(path, { method: "DELETE", headers }),
};

/** SSE uses the BFF session cookie via EventSource withCredentials. */
export function sseUrl(path: string): string {
  const separator = path.includes("?") ? "&" : "?";
  if (SECURITY_MODE === "oidc") {
    return `${API_BASE}${path}`;
  }
  const apiKey = loadSession()?.apiKey ?? "";
  return `${API_BASE}${path}${separator}apiKey=${encodeURIComponent(apiKey)}`;
}
