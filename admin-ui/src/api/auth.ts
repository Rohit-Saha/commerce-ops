import { API_BASE, ApiError, unwrapData } from "./client";
import { SECURITY_MODE } from "../lib/authSession";

export interface LoginResponse {
  username: string;
  apiKey: string;
}

export interface AuthMeResponse {
  username: string;
}

/** Login is public — does not attach X-API-Key. */
export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });

  if (!res.ok) {
    let parsedBody: unknown;
    try {
      parsedBody = await res.json();
    } catch {
      // ignore
    }
    const message =
      (parsedBody as { message?: string } | undefined)?.message ??
      `Login failed: ${res.status} ${res.statusText}`;
    throw new ApiError(res.status, message, parsedBody);
  }

  return unwrapData<LoginResponse>(await res.json());
}

/**
 * Legacy: pass API key. OIDC: cookie session (`credentials: include`); credential ignored.
 */
export async function fetchMe(credential?: string): Promise<AuthMeResponse> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const init: RequestInit = { headers };
  if (SECURITY_MODE === "oidc") {
    init.credentials = "include";
  } else if (credential) {
    headers["X-API-Key"] = credential;
  }
  const res = await fetch(`${API_BASE}/api/auth/me`, init);

  if (!res.ok) {
    let parsedBody: unknown;
    try {
      parsedBody = await res.json();
    } catch {
      // ignore
    }
    const message =
      (parsedBody as { message?: string } | undefined)?.message ??
      `Session check failed: ${res.status}`;
    throw new ApiError(res.status, message, parsedBody);
  }

  return unwrapData<AuthMeResponse>(await res.json());
}
