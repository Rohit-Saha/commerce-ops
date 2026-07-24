export type OrderStatus =
  | "PENDING"
  | "RESERVING"
  | "RESERVED"
  | "PAYMENT_PENDING"
  | "PAID"
  | "SHIPPING"
  | "DELIVERED"
  | "COMPLETED"
  | "CANCELLED"
  | "FAILED";

export type SagaStatus =
  | "STARTED"
  | "RESERVING"
  | "RESERVED"
  | "PAYING"
  | "PAID"
  | "SHIPPING"
  | "COMPLETED"
  | "COMPENSATING"
  | "COMPENSATED"
  | "FAILED_NEEDS_ATTENTION";

export type PaymentStatus = "PENDING" | "AUTHORIZED" | "CAPTURED" | "FAILED" | "REFUNDED" | string;

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


export interface OrderLine {
  sku: string;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  id: string;
  customerId: string;
  status: OrderStatus;
  totalAmount: number;
  currency: string;
  idempotencyKey: string;
  lines: OrderLine[];
  createdAt: string;
  updatedAt: string;
}

export interface StockItem {
  sku: string;
  name: string;
  availableQty: number;
  reservedQty: number;
  unitPrice: number;
}

export interface Saga {
  id: number;
  orderId: string;
  status: SagaStatus;
  currentStep: string | null;
  reservationId: string | null;
  paymentId: string | null;
  shipmentId: string | null;
  customerId: string | null;
  retryCount: number;
  stepDeadline: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Payment {
  id: number;
  orderId: string;
  sagaId: string | null;
  amount: number;
  currency: string;
  status: PaymentStatus;
  idempotencyKey: string | null;
  failureReason: string | null;
  createdAt: string;
}

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
  sagaId: string | null;
  trackingNumber: string | null;
  carrier: string | null;
  carrierOrderId: string | null;
  labelUrl: string | null;
  status: ShipmentStatus;
  failureReason: string | null;
  statusReason: string | null;
  statusUpdatedAt: string | null;
  createdAt: string;
  events?: ShipmentEvent[];
}

export interface TimelineEntry {
  timestamp: string;
  label: string;
  detail?: string | null;
  source?: "order" | "saga" | "payment" | "shipment" | string;
}
