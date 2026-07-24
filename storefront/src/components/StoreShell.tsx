import { Link, NavLink, Outlet } from "react-router-dom";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "../lib/auth";
import { useCart } from "../lib/useCart";
import { BrandMark, CartIcon, UserIcon } from "./Icons";

function initialsFromName(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0] ?? ""}${parts[1][0] ?? ""}`.toUpperCase();
}

export function StoreShell() {
  const { count } = useCart();
  const { session, ready, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const signedIn = Boolean(ready && session);
  const badgeLabel = signedIn ? session!.customer.displayName.split(/\s+/)[0] : "Guest";
  const badgeInitials = signedIn ? initialsFromName(session!.customer.displayName) : null;

  useEffect(() => {
    function onDocClick(event: MouseEvent) {
      if (!menuRef.current?.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDocClick);
      document.removeEventListener("keydown", onKey);
    };
  }, []);

  return (
    <div className="store">
      <header className="store-header">
        <Link to="/" className="store-brand" aria-label="Northline Goods home">
          <BrandMark className="store-brand__mark" />
          <span className="store-brand__text">
            <span className="store-brand__name">Northline</span>
            <span className="store-brand__tag">Goods</span>
          </span>
        </Link>

        <div className="store-header__right">
          <nav className="store-nav store-nav--primary" aria-label="Primary">
            <NavLink to="/" end className={({ isActive }) => (isActive ? "active" : undefined)}>
              Home
            </NavLink>
            <NavLink to="/catalog" className={({ isActive }) => (isActive ? "active" : undefined)}>
              Catalog
            </NavLink>
          </nav>

          <NavLink
            to="/cart"
            className={({ isActive }) => `store-icon-link${isActive ? " active" : ""}`}
            aria-label={count > 0 ? `Cart, ${count} items` : "Cart"}
          >
            <CartIcon />
            {count > 0 ? <span className="cart-badge">{count}</span> : null}
          </NavLink>

          <div className="account-menu" ref={menuRef}>
            <button
              type="button"
              className={`user-badge${menuOpen ? " open" : ""}${signedIn ? " signed-in" : " guest"}`}
              aria-expanded={menuOpen}
              aria-haspopup="menu"
              aria-label={signedIn ? `Account menu for ${session!.customer.displayName}` : "Guest account menu"}
              onClick={() => setMenuOpen((v) => !v)}
            >
              <span className="user-badge__avatar" aria-hidden="true">
                {badgeInitials ? badgeInitials : <UserIcon className="user-badge__icon" />}
              </span>
              <span className="user-badge__meta">
                <span className="user-badge__name">{badgeLabel}</span>
                <span className="user-badge__hint">{signedIn ? "Account" : "Sign in"}</span>
              </span>
            </button>

            {menuOpen ? (
              <div className="account-menu__panel" role="menu">
                {signedIn ? (
                  <>
                    <div className="account-menu__head">
                      <div className="user-badge__avatar user-badge__avatar--lg" aria-hidden="true">
                        {badgeInitials}
                      </div>
                      <div>
                        <strong>{session!.customer.displayName}</strong>
                        <span>{session!.customer.email}</span>
                      </div>
                    </div>
                    <Link to="/account/profile" role="menuitem" onClick={() => setMenuOpen(false)}>
                      Profile
                    </Link>
                    <Link to="/account/orders" role="menuitem" onClick={() => setMenuOpen(false)}>
                      Orders
                    </Link>
                    <Link to="/account/addresses" role="menuitem" onClick={() => setMenuOpen(false)}>
                      Addresses
                    </Link>
                    <button
                      type="button"
                      role="menuitem"
                      className="account-menu__signout"
                      onClick={() => {
                        setMenuOpen(false);
                        logout();
                      }}
                    >
                      Sign out
                    </button>
                  </>
                ) : (
                  <>
                    <div className="account-menu__head">
                      <div className="user-badge__avatar user-badge__avatar--lg guest" aria-hidden="true">
                        <UserIcon className="user-badge__icon" />
                      </div>
                      <div>
                        <strong>Guest</strong>
                        <span>Sign in to manage orders and addresses</span>
                      </div>
                    </div>
                    <Link to="/login" role="menuitem" onClick={() => setMenuOpen(false)}>
                      Sign in
                    </Link>
                    <Link to="/register" role="menuitem" onClick={() => setMenuOpen(false)}>
                      Register
                    </Link>
                  </>
                )}
              </div>
            ) : null}
          </div>
        </div>
      </header>

      <main className="store-main">
        <Outlet />
      </main>

      <footer className="store-footer">
        <div className="store-footer__brand">
          <div className="store-footer__logo">
            <BrandMark className="store-footer__mark" />
            <span className="store-footer__name">Northline Goods</span>
          </div>
          <p className="store-footer__blurb">
            Everyday essentials with live inventory — made to be used, not just displayed.
          </p>
        </div>
        <div className="store-footer__cols">
          <div className="store-footer__col">
            <h3>Shop</h3>
            <Link to="/catalog">Catalog</Link>
            <Link to="/cart">Cart</Link>
          </div>
          <div className="store-footer__col">
            <h3>Account</h3>
            {session ? (
              <>
                <Link to="/account/profile">Profile</Link>
                <Link to="/account/orders">Orders</Link>
                <Link to="/account/addresses">Addresses</Link>
              </>
            ) : (
              <>
                <Link to="/login">Sign in</Link>
                <Link to="/register">Register</Link>
              </>
            )}
          </div>
        </div>
        <p className="store-footer__meta">Powered by commerce-ops</p>
      </footer>
    </div>
  );
}
