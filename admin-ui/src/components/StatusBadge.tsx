type BadgeTone = "neutral" | "info" | "warning" | "success" | "danger" | "accent";

const ORDER_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: "neutral",
  RESERVING: "info",
  RESERVED: "info",
  PAYMENT_PENDING: "warning",
  PAID: "accent",
  SHIPPING: "info",
  DELIVERED: "success",
  COMPLETED: "success",
  CANCELLED: "neutral",
  FAILED: "danger",
};

const SAGA_STATUS_TONE: Record<string, BadgeTone> = {
  STARTED: "neutral",
  RESERVING: "info",
  RESERVED: "info",
  PAYING: "warning",
  PAID: "accent",
  SHIPPING: "info",
  COMPLETED: "success",
  COMPENSATING: "warning",
  COMPENSATED: "neutral",
  FAILED_NEEDS_ATTENTION: "danger",
};

const GENERIC_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: "neutral",
  AUTHORIZED: "info",
  CAPTURED: "success",
  FAILED: "danger",
  REFUNDED: "neutral",
  CREATED: "info",
  LABEL_CREATED: "info",
  PICKED_UP: "info",
  IN_TRANSIT: "info",
  OUT_FOR_DELIVERY: "warning",
  SHIPPED: "info",
  DELIVERED: "success",
  RTO: "danger",
  CANCELLED: "neutral",
};

function toneFor(status: string, table: Record<string, BadgeTone>): BadgeTone {
  return table[status] ?? GENERIC_STATUS_TONE[status] ?? "neutral";
}

interface StatusBadgeProps {
  status: string;
  kind?: "order" | "saga" | "generic";
}

export function StatusBadge({ status, kind = "generic" }: StatusBadgeProps) {
  const table = kind === "order" ? ORDER_STATUS_TONE : kind === "saga" ? SAGA_STATUS_TONE : GENERIC_STATUS_TONE;
  const tone = toneFor(status, table);
  return <span className={`badge badge-${tone}`}>{status.replace(/_/g, " ")}</span>;
}
