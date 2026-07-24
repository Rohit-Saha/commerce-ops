import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { LoadingState, Spinner } from "../components/Spinner";
import { useAuth } from "../lib/auth";
import { useNotify } from "../lib/notify";

export function RegisterPage() {
  const { session, ready, register } = useAuth();
  const { notifyError } = useNotify();
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);

  if (!ready) {
    return <LoadingState className="page-narrow loading-state--page" label="Checking session…" />;
  }

  if (session) {
    return <Navigate to="/" replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    try {
      await register(email.trim(), password, displayName.trim());
      navigate("/account/addresses", { replace: true });
    } catch (err) {
      notifyError(err, "register");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="page-narrow">
      <h1>Create account</h1>
      <p className="lede">Save addresses and track orders after you place them.</p>
      <form className="auth-form" onSubmit={onSubmit}>
        <label>
          Display name
          <input
            type="text"
            autoComplete="name"
            required
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
          />
        </label>
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
            autoComplete="new-password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button className="btn btn-primary" type="submit" disabled={pending}>
          {pending ? <Spinner size="sm" label="Creating" /> : null}
          {pending ? "Creating…" : "Create account"}
        </button>
      </form>
      <p className="auth-switch">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </div>
  );
}
