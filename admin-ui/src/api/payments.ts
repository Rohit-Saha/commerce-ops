import { api } from "./client";
import type { Payment } from "./types";

export function listPaymentsByOrder(orderId: string): Promise<Payment[]> {
  return api.get<Payment[]>(`/api/payments/by-order/${orderId}`);
}
