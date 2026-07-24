import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { ConnectionIndicator } from "./ConnectionIndicator";
import { useAuth } from "../lib/auth";

const NAV_ITEMS = [
  { to: "/", label: "Orders", icon: "OR", end: true },
  { to: "/demo", label: "Demo flows", icon: "DM" },
  { to: "/inventory", label: "Inventory", icon: "IN" },
  { to: "/sagas", label: "Sagas", icon: "SG" },
];

export function AppShell() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  function onLogout() {
    logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-sidebar__section-label">Console</div>
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `app-nav-link${isActive ? " active" : ""}`}
          >
            <span className="app-nav-link__icon">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
        <a
          className="app-nav-link"
          href="http://localhost:1337/admin"
          target="_blank"
          rel="noreferrer"
        >
          <span className="app-nav-link__icon">CM</span>
          Open Strapi CMS
        </a>
      </aside>
      <header className="app-header">
        <div className="app-header__brand">
          <span className="app-header__brand-dot" />
          commerce-ops
        </div>
        <div className="app-header__actions">
          <ConnectionIndicator />
          <span className="app-header__user mono">{session?.username}</span>
          <button className="btn btn-sm" type="button" onClick={onLogout}>
            Sign out
          </button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
