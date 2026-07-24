const SESSION_KEY = "commerce-ops.admin.session";

export type SecurityMode = "legacy" | "oidc";

export interface AuthSession {
  username: string;
  /** Legacy API key auth */
  apiKey?: string;
  /** OIDC: cookie session on gateway; no browser token storage */
  mode: SecurityMode;
}

export const SECURITY_MODE: SecurityMode =
  (import.meta.env.VITE_SECURITY_MODE as SecurityMode | undefined) === "oidc" ? "oidc" : "legacy";

export function loadSession(): AuthSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AuthSession & { accessToken?: string };
    if (!parsed?.username) return null;
    if (parsed.mode === "oidc") {
      return { username: parsed.username, mode: "oidc" };
    }
    if (!parsed.apiKey) {
      return null;
    }
    return { username: parsed.username, apiKey: parsed.apiKey, mode: "legacy" };
  } catch {
    return null;
  }
}

export function saveSession(session: AuthSession): void {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  sessionStorage.removeItem(SESSION_KEY);
}
