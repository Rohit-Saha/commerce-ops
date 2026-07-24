import { api } from "./client";

export interface ChaosState {
  failureRate: number;
}

export function setPaymentChaos(failureRate: number): Promise<ChaosState> {
  return api.post<ChaosState>(`/api/payments/chaos?failureRate=${encodeURIComponent(String(failureRate))}`);
}

export function setShippingChaos(failureRate: number): Promise<ChaosState> {
  return api.post<ChaosState>(`/api/shipments/chaos?failureRate=${encodeURIComponent(String(failureRate))}`);
}
