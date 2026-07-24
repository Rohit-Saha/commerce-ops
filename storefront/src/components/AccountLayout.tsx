import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../lib/auth";

export function AccountLayout() {
  const { session } = useAuth();

  return (
    <div className="page-inner account-page">
      <header className="account-hero">
        <p className="eyebrow">Your account</p>
        <h1>{session?.customer.displayName ?? "Account"}</h1>
        <p className="lede">{session?.customer.email}</p>
      </header>

      <nav className="account-tabs" aria-label="Account sections">
        <NavLink to="/account/profile" className={({ isActive }) => (isActive ? "active" : undefined)}>
          Profile
        </NavLink>
        <NavLink to="/account/orders" className={({ isActive }) => (isActive ? "active" : undefined)}>
          Orders
        </NavLink>
        <NavLink to="/account/addresses" className={({ isActive }) => (isActive ? "active" : undefined)}>
          Addresses
        </NavLink>
      </nav>

      <Outlet />
    </div>
  );
}
