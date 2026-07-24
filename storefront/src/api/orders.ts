import { api } from "./client";
import type { CreateOrderInput, Order } from "./types";
import { sessionCredential } from "../lib/authSession";

export function createOrder(input: CreateOrderInput, idempotencyKey: string): Promise<Order> {
  return api.post<Order>("/api/orders", input, { "Idempotency-Key": idempotencyKey }, sessionCredential());
}

export function getOrder(id: string): Promise<Order> {
  return api.get<Order>(`/api/orders/${encodeURIComponent(id)}`, undefined, sessionCredential());
}

export function listMyOrders(): Promise<Order[]> {
  return api.get<Order[]>("/api/orders/mine", undefined, sessionCredential());
}

export function cancelOrder(id: string, reason?: string): Promise<Order> {
  return api.post<Order>(
    `/api/orders/${encodeURIComponent(id)}/cancel`,
    { reason: reason ?? "Customer requested cancellation" },
    undefined,
    sessionCredential(),
  );
}

export function randomIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
