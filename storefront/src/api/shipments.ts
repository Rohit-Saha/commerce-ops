import { api } from "./client";
import { sessionCredential } from "../lib/authSession";

export type ShipmentStatus =
  | "CREATED"
  | "PICKED_UP"
  | "IN_TRANSIT"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "RTO"
  | "CANCELLED"
  | "FAILED"
  | string;

export interface ShipmentEvent {
  id: number;
  status: ShipmentStatus;
  rawCode: string | null;
  message: string | null;
  occurredAt: string;
}

export interface Shipment {
  id: number;
  orderId: string;
  trackingNumber: string | null;
  carrier: string | null;
  labelUrl: string | null;
  status: ShipmentStatus;
  statusReason: string | null;
  statusUpdatedAt: string | null;
  createdAt: string;
  events?: ShipmentEvent[];
}

export function listShipmentsByOrder(orderId: string): Promise<Shipment[]> {
  return api.get<Shipment[]>(
    `/api/shipments/by-order/${encodeURIComponent(orderId)}`,
    undefined,
    sessionCredential(),
  );
}
