import { api } from "./client";
import type { Shipment, ShipmentEvent } from "./types";

export function listShipmentsByOrder(orderId: string): Promise<Shipment[]> {
  return api.get<Shipment[]>(`/api/shipments/by-order/${orderId}`);
}

export function getShipment(id: number): Promise<Shipment> {
  return api.get<Shipment>(`/api/shipments/${id}`);
}

export function listShipmentEvents(id: number): Promise<ShipmentEvent[]> {
  return api.get<ShipmentEvent[]>(`/api/shipments/${id}/events`);
}

export function advanceShipment(id: number): Promise<Shipment> {
  return api.post<Shipment>(`/api/shipments/${id}/advance`);
}

export function createShipment(orderId: string): Promise<Shipment> {
  return api.post<Shipment>("/api/shipments", { orderId });
}
