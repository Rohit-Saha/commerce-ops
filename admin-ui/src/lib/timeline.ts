import type { Order, Payment, Saga, Shipment, TimelineEntry } from "../api/types";

/**
 * Builds a best-effort timeline from order + saga + payment + shipment data
 * when the dedicated GET /api/orders/{id}/timeline endpoint is unavailable
 * (e.g. 404 because the gateway/service hasn't implemented it yet).
 */
export function buildFallbackTimeline(
  order: Order | undefined,
  saga: Saga | undefined | null,
  payments: Payment[] | undefined,
  shipments: Shipment[] | undefined,
): TimelineEntry[] {
  const entries: TimelineEntry[] = [];

  if (order) {
    entries.push({
      timestamp: order.createdAt,
      label: "Order created",
      detail: `${order.lines.length} line(s) · ${order.currency} ${order.totalAmount}`,
      source: "order",
    });
    if (order.updatedAt && order.updatedAt !== order.createdAt) {
      entries.push({
        timestamp: order.updatedAt,
        label: `Order status: ${order.status.replace(/_/g, " ")}`,
        source: "order",
      });
    }
  }

  if (saga) {
    entries.push({
      timestamp: saga.updatedAt ?? saga.createdAt,
      label: `Saga ${saga.status.replace(/_/g, " ")}`,
      detail: saga.currentStep ? `Current step: ${saga.currentStep}` : saga.lastError ?? undefined,
      source: "saga",
    });
  }

  for (const payment of payments ?? []) {
    entries.push({
      timestamp: payment.createdAt,
      label: `Payment ${payment.status.replace(/_/g, " ")}`,
      detail: payment.failureReason ?? undefined,
      source: "payment",
    });
  }

  for (const shipment of shipments ?? []) {
    entries.push({
      timestamp: shipment.createdAt,
      label: `Shipment ${shipment.status.replace(/_/g, " ")}`,
      detail: shipment.trackingNumber ? `Tracking: ${shipment.trackingNumber}` : shipment.failureReason ?? undefined,
      source: "shipment",
    });
  }

  return entries
    .filter((entry) => Boolean(entry.timestamp))
    .sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
}
