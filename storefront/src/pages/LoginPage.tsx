import { FormEvent, useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { LoadingState, Spinner } from "../components/Spinner";
import { useAuth } from "../lib/auth";
import { useNotify } from "../lib/notify";

export function LoginPage() {
  const { session, ready, login } = useAuth();
  const { notifyError } = useNotify();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);

  if (!ready) {
    return <LoadingState className="page-narrow loading-state--page" label="Checking session…" />;
  }

  if (session) {
    return <Navigate to={from} replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      notifyError(err, "login");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="page-narrow">
      <h1>Sign in</h1>
      <p className="lede">Sign in to checkout and manage saved addresses.</p>
      <form className="auth-form" onSubmit={onSubmit}>
        <label>
          Email
          <input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label>
          Password
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button className="btn btn-primary" type="submit" disabled={pending}>
          {pending ? <Spinner size="sm" label="Signing in" /> : null}
          {pending ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className="auth-switch">
        New here? <Link to="/register">Create an account</Link>
      </p>
    </div>
  );
}
