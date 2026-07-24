import type { CustomerProfile } from "../api/types";

const STORAGE_KEY = "commerce-ops.storefront.session";

export type SecurityMode = "legacy" | "oidc";

export const SECURITY_MODE: SecurityMode =
  (import.meta.env.VITE_SECURITY_MODE as string | undefined) === "oidc" ? "oidc" : "legacy";

export interface CustomerSession {
  /** Legacy customer JWT. OIDC uses gateway session cookie instead. */
  token?: string;
  customer: CustomerProfile;
  mode?: SecurityMode;
}

export function loadSession(): CustomerSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CustomerSession;
    if (!parsed?.customer?.id) return null;
    if (SECURITY_MODE === "oidc") {
      return { customer: parsed.customer, mode: "oidc" };
    }
    if (!parsed.token) return null;
    return { ...parsed, mode: "legacy" };
  } catch {
    return null;
  }
}

export function saveSession(session: CustomerSession): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

/**
 * Credential for API calls: legacy customer JWT, or undefined in OIDC (cookie session).
 * Throws when the user is not signed in.
 */
export function sessionCredential(session?: CustomerSession | null): string | undefined {
  const current = session ?? loadSession();
  if (SECURITY_MODE === "oidc") {
    if (!current?.customer?.id) {
      throw new Error("Login required");
    }
    return undefined;
  }
  if (!current?.token) {
    throw new Error("Login required");
  }
  return current.token;
}
