import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { fetchMe, login as loginRequest } from "../api/auth";
import {
  clearSession,
  loadSession,
  saveSession,
  SECURITY_MODE,
  type AuthSession,
} from "../lib/authSession";
import { setUnauthorizedHandler } from "../api/client";
import { loginOidc, logoutOidc } from "../lib/bffAuth";

interface AuthContextValue {
  session: AuthSession | null;
  ready: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  securityMode: typeof SECURITY_MODE;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
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
          const me = await fetchMe();
          if (cancelled) return;
          const next: AuthSession = { username: me.username, mode: "oidc" };
          saveSession(next);
          setSession(next);
        } catch {
          clearSession();
          if (!cancelled) setSession(null);
        } finally {
          if (!cancelled) setReady(true);
        }
        return;
      }

      const stored = loadSession();
      if (!stored?.apiKey) {
        if (!cancelled) setReady(true);
        return;
      }
      try {
        await fetchMe(stored.apiKey);
        if (!cancelled) setSession(stored);
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

  const login = useCallback(async (username: string, password: string) => {
    if (SECURITY_MODE === "oidc") {
      loginOidc();
      return;
    }
    const response = await loginRequest(username, password);
    const next: AuthSession = {
      username: response.username,
      apiKey: response.apiKey,
      mode: "legacy",
    };
    saveSession(next);
    setSession(next);
  }, []);

  const value = useMemo(
    () => ({
      session,
      ready,
      login,
      logout,
      securityMode: SECURITY_MODE,
    }),
    [session, ready, login, logout],
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
