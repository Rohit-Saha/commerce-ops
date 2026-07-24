import { api } from "./client";
import type { Saga } from "./types";

export function listSagas(): Promise<Saga[]> {
  return api.get<Saga[]>("/api/sagas");
}

export function getSaga(id: number | string): Promise<Saga> {
  return api.get<Saga>(`/api/sagas/${id}`);
}

export function getSagaByOrderId(orderId: string): Promise<Saga> {
  return api.get<Saga>(`/api/sagas/by-order/${orderId}`);
}
