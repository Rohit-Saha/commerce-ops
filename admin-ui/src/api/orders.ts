import { api } from "./client";
import { buildFallbackTimeline } from "../lib/timeline";
import type { Order, Payment, Saga, Shipment, TimelineEntry } from "./types";

export interface CreateOrderLineInput {
  sku: string;
  quantity: number;
  unitPrice: number;
}

export interface CreateOrderInput {
  customerId: string;
  currency: string;
  lines: CreateOrderLineInput[];
}

/** Shape returned by api-gateway GET /api/orders/{id}/timeline. */
interface OrderTimelineAggregate {
  orderId: string;
  order?: Order | null;
  saga?: Saga | null;
  payments?: Payment[] | null;
  shipments?: Shipment[] | null;
}

export function listOrders(): Promise<Order[]> {
  return api.get<Order[]>("/api/orders");
}

export function getOrder(id: string): Promise<Order> {
  return api.get<Order>(`/api/orders/${id}`);
}

export function createOrder(input: CreateOrderInput, idempotencyKey: string): Promise<Order> {
  return api.post<Order>("/api/orders", input, { "Idempotency-Key": idempotencyKey });
}

export function cancelOrder(id: string, reason?: string): Promise<Order> {
  return api.post<Order>(`/api/orders/${id}/cancel`, reason ? { reason } : undefined);
}

export async function getOrderTimeline(id: string): Promise<TimelineEntry[]> {
  const data = await api.get<TimelineEntry[] | OrderTimelineAggregate>(`/api/orders/${id}/timeline`);
  if (Array.isArray(data)) {
    return data;
  }
  // Gateway returns a stitched aggregate; derive display entries client-side.
  return buildFallbackTimeline(
    data.order ?? undefined,
    data.saga ?? null,
    data.payments ?? [],
    data.shipments ?? [],
  );
}

export function randomIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export type DemoScenarioId = "happy" | "payment-fail" | "shipping-fail" | "inventory-fail";

export interface DemoScenario {
  id: DemoScenarioId;
  title: string;
  description: string;
  build: () => CreateOrderInput;
}

function demoCustomer(prefix: string): string {
  return `${prefix}${Math.floor(Math.random() * 10000)}`;
}

export const DEMO_SCENARIOS: DemoScenario[] = [
  {
    id: "happy",
    title: "Happy path",
    description: "Reserve → pay → ship → complete. Amount $29.00 (avoids .99 chaos rule).",
    build: () => ({
      customerId: demoCustomer("cust-"),
      currency: "USD",
      lines: [{ sku: "SKU-TEE-001", quantity: 1, unitPrice: 29.0 }],
    }),
  },
  {
    id: "payment-fail",
    title: "Payment failure",
    description: "Amount ends in .99 → capture fails → saga releases inventory.",
    build: () => ({
      customerId: demoCustomer("cust-"),
      currency: "USD",
      lines: [{ sku: "SKU-TEE-001", quantity: 1, unitPrice: 19.99 }],
    }),
  },
  {
    id: "shipping-fail",
    title: "Shipping failure",
    description: "Customer id NOSHIP-* → shipment fails → refund then release inventory.",
    build: () => ({
      customerId: demoCustomer("NOSHIP-"),
      currency: "USD",
      lines: [{ sku: "SKU-TEE-001", quantity: 1, unitPrice: 12.0 }],
    }),
  },
  {
    id: "inventory-fail",
    title: "Insufficient stock",
    description: "Requests more units than available → reservation fails early.",
    build: () => ({
      customerId: demoCustomer("cust-"),
      currency: "USD",
      lines: [{ sku: "SKU-HAT-001", quantity: 999, unitPrice: 15.0 }],
    }),
  },
];

export function buildDemoOrder(scenarioId: DemoScenarioId = "happy"): CreateOrderInput {
  const scenario = DEMO_SCENARIOS.find((s) => s.id === scenarioId) ?? DEMO_SCENARIOS[0];
  return scenario.build();
}
