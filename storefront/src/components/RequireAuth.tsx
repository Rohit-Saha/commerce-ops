import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { LoadingState } from "./Spinner";

export function RequireAuth() {
  const { session, ready } = useAuth();
  const location = useLocation();

  if (!ready) {
    return <LoadingState className="page-narrow loading-state--page" label="Checking session…" />;
  }

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}
