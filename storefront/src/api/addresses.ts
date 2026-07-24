import { api } from "./client";
import type { Address, AddressInput } from "./types";

export function listAddresses(token?: string | null): Promise<Address[]> {
  return api.get<Address[]>("/api/customers/me/addresses", undefined, token);
}

export function createAddress(token: string | null | undefined, input: AddressInput): Promise<Address> {
  return api.post<Address>("/api/customers/me/addresses", input, undefined, token);
}

export function updateAddress(
  token: string | null | undefined,
  id: string,
  input: AddressInput,
): Promise<Address> {
  return api.put<Address>(`/api/customers/me/addresses/${encodeURIComponent(id)}`, input, undefined, token);
}

export function deleteAddress(token: string | null | undefined, id: string): Promise<void> {
  return api.delete<void>(`/api/customers/me/addresses/${encodeURIComponent(id)}`, undefined, token);
}

export function setDefaultAddress(token: string | null | undefined, id: string): Promise<Address> {
  return api.put<Address>(
    `/api/customers/me/addresses/${encodeURIComponent(id)}/default`,
    undefined,
    undefined,
    token,
  );
}
