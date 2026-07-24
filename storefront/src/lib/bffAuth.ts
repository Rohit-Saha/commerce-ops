import { API_BASE } from "../api/client";
import { SECURITY_MODE } from "./authSession";

/** OIDC via gateway BFF — browser never calls Keycloak. */
export function loginOidc(): void {
  window.location.href = `${API_BASE}/api/auth/login?client=storefront`;
}

export function registerOidc(): void {
  window.location.href = `${API_BASE}/api/auth/login?client=storefront&register=true`;
}

export function logoutOidc(): void {
  window.location.href = `${API_BASE}/api/auth/logout?client=storefront`;
}

export function isOidcMode(): boolean {
  return SECURITY_MODE === "oidc";
}
