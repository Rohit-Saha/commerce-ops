import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";

export function LoginPage() {
  const { session, ready, login, securityMode } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const from = (location.state as { from?: string } | null)?.from ?? "/";

  if (ready && session) {
    return <Navigate to={from} replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username.trim(), password);
      if (securityMode === "legacy") {
        navigate(from, { replace: true });
      }
      // OIDC redirects away to Keycloak
    } catch (err) {
      setError((err as Error).message || "Login failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={onSubmit}>
        <div className="login-card__brand">
          <span className="app-header__brand-dot" />
          commerce-ops
        </div>
        <h1>Admin sign in</h1>
        <p className="login-card__lead">
          {securityMode === "oidc"
            ? "Sign in with Keycloak (OIDC) to manage orders and inventory."
            : "Sign in to manage orders, inventory, and demo flows."}
        </p>

        {securityMode === "legacy" && (
          <>
            <label className="form-field">
              <span>Username</span>
              <input
                className="input input-wide"
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={submitting}
              />
            </label>
            <label className="form-field">
              <span>Password</span>
              <input
                className="input input-wide"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={submitting}
              />
            </label>
          </>
        )}

        {error && <div className="error-banner" style={{ marginBottom: 0 }}>{error}</div>}

        <button className="btn btn-primary login-card__submit" type="submit" disabled={submitting || !ready}>
          {submitting
            ? "Signing in…"
            : securityMode === "oidc"
              ? "Continue with Keycloak"
              : "Sign in"}
        </button>

        <p className="login-card__hint">
          {securityMode === "oidc" ? (
            <>
              Demo user: <span className="mono">admin</span> / <span className="mono">admin</span> (Keycloak realm
              commerce-ops)
            </>
          ) : (
            <>
              Local demo credentials: <span className="mono">admin</span> / <span className="mono">admin</span>
            </>
          )}
        </p>
      </form>
    </div>
  );
}
