import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  fetchMe,
  login as loginRequest,
  register as registerRequest,
  updateProfile as updateProfileRequest,
} from "../api/auth";
import {
  clearSession,
  loadSession,
  saveSession,
  SECURITY_MODE,
  type CustomerSession,
} from "./authSession";
import { setUnauthorizedHandler } from "../api/client";
import type { CustomerProfile } from "../api/types";
import { loginOidc, logoutOidc, registerOidc } from "./bffAuth";

interface AuthContextValue {
  session: CustomerSession | null;
  ready: boolean;
  securityMode: typeof SECURITY_MODE;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  updateProfile: (input: {
    displayName?: string;
    currentPassword?: string;
    newPassword?: string;
  }) => Promise<CustomerProfile>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<CustomerSession | null>(null);
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
    if (SECURITY_MODE === "oidc") {
      logoutOidc();
      return;
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession();
      setSession(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function restore() {
      if (SECURITY_MODE === "oidc") {
        try {
          const customer = await fetchMe();
          if (!cancelled) {
            const next: CustomerSession = { customer, mode: "oidc" };
            saveSession(next);
            setSession(next);
          }
        } catch {
          clearSession();
          if (!cancelled) setSession(null);
        } finally {
          if (!cancelled) setReady(true);
        }
        return;
      }

      const stored = loadSession();
      if (!stored) {
        if (!cancelled) setReady(true);
        return;
      }
      try {
        const customer = await fetchMe(stored.token);
        if (!cancelled) {
          const next = { token: stored.token, customer, mode: "legacy" as const };
          saveSession(next);
          setSession(next);
        }
      } catch {
        clearSession();
        if (!cancelled) setSession(null);
      } finally {
        if (!cancelled) setReady(true);
      }
    }

    void restore();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    if (SECURITY_MODE === "oidc") {
      loginOidc();
      return;
    }
    const response = await loginRequest(email, password);
    const next: CustomerSession = {
      token: response.token,
      customer: response.customer,
      mode: "legacy",
    };
    saveSession(next);
    setSession(next);
  }, []);

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    if (SECURITY_MODE === "oidc") {
      registerOidc();
      return;
    }
    const response = await registerRequest(email, password, displayName);
    const next: CustomerSession = {
      token: response.token,
      customer: response.customer,
      mode: "legacy",
    };
    saveSession(next);
    setSession(next);
  }, []);

  const updateProfile = useCallback(
    async (input: { displayName?: string; currentPassword?: string; newPassword?: string }) => {
      const token = SECURITY_MODE === "oidc" ? undefined : loadSession()?.token ?? session?.token;
      if (SECURITY_MODE !== "oidc" && !token) {
        throw new Error("Login required");
      }
      const customer = await updateProfileRequest(token, input);
      setSession((prev) => {
        if (!prev) return prev;
        const next = { ...prev, customer };
        saveSession(next);
        return next;
      });
      return customer;
    },
    [session?.token],
  );

  const value = useMemo(
    () => ({ session, ready, securityMode: SECURITY_MODE, login, register, updateProfile, logout }),
    [session, ready, login, register, updateProfile, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}
