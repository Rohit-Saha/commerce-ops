import { api } from "./client";
import type { AuthResponse, CustomerProfile } from "./types";

export function register(email: string, password: string, displayName: string): Promise<AuthResponse> {
  return api.post<AuthResponse>("/api/customers/register", { email, password, displayName });
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return api.post<AuthResponse>("/api/customers/login", { email, password });
}

export function fetchMe(token?: string | null): Promise<CustomerProfile> {
  return api.get<CustomerProfile>("/api/customers/me", undefined, token);
}

export function updateProfile(
  token: string | null | undefined,
  body: { displayName?: string; currentPassword?: string; newPassword?: string },
): Promise<CustomerProfile> {
  return api.put<CustomerProfile>("/api/customers/me", body, undefined, token);
}
