import { SECURITY_MODE } from "../lib/authSession";

export const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";
export const STOREFRONT_API_KEY = import.meta.env.VITE_STOREFRONT_API_KEY ?? "";

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

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

interface ApiEnvelope<T> {
  success?: boolean;
  message?: string;
  data?: T;
  meta?: unknown;
}

function unwrapData<T>(parsed: unknown): T {
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
  token?: string | null;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { headers, body, token, ...rest } = options;

  const auth: Record<string, string> = {};
  if (SECURITY_MODE === "oidc") {
    // Gateway BFF session cookie authenticates the request.
  } else {
    auth["X-API-Key"] = STOREFRONT_API_KEY;
    if (token) {
      auth.Authorization = `Bearer ${token}`;
    }
  }

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
    if (res.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }
    let parsedBody: unknown;
    try {
      parsedBody = await res.json();
    } catch {
      // no JSON body
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
  get: <T>(path: string, headers?: Record<string, string>, token?: string | null) =>
    request<T>(path, { method: "GET", headers, token }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>, token?: string | null) =>
    request<T>(path, { method: "POST", body, headers, token }),
  put: <T>(path: string, body?: unknown, headers?: Record<string, string>, token?: string | null) =>
    request<T>(path, { method: "PUT", body, headers, token }),
  delete: <T>(path: string, headers?: Record<string, string>, token?: string | null) =>
    request<T>(path, { method: "DELETE", headers, token }),
};
