import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cancelOrder, getOrder, getOrderTimeline } from "../api/orders";
import { getSagaByOrderId } from "../api/sagas";
import { listPaymentsByOrder } from "../api/payments";
import { listShipmentsByOrder, advanceShipment, createShipment } from "../api/shipments";
import { downloadInvoicePdf, getInvoiceByOrder } from "../api/invoices";
import { isNotFound } from "../api/client";
import { StatusBadge } from "../components/StatusBadge";
import { formatDateTime, formatMoney, shortId } from "../lib/format";
import { buildFallbackTimeline } from "../lib/timeline";
import { useConnectOrdersStream } from "../hooks/useOrdersStream";
import type { TimelineEntry } from "../api/types";

const CANCELLABLE_STATUSES = new Set(["PENDING", "RESERVING", "RESERVED", "PAYMENT_PENDING", "PAID"]);

const ADVANCE_ORDER = [
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
] as const;

const TERMINAL_SHIPMENT = new Set(["FAILED", "DELIVERED", "RTO", "CANCELLED"]);

function nextShipmentStatus(status: string): string | null {
  const idx = ADVANCE_ORDER.indexOf(status as (typeof ADVANCE_ORDER)[number]);
  if (idx < 0 || idx >= ADVANCE_ORDER.length - 1) {
    return null;
  }
  return ADVANCE_ORDER[idx + 1];
}

export function OrderDetailPage() {
  useConnectOrdersStream();
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const [cancelReason, setCancelReason] = useState("");
  const [showCancelForm, setShowCancelForm] = useState(false);

  const orderQuery = useQuery({
    queryKey: ["order", id],
    queryFn: () => getOrder(id as string),
    enabled: Boolean(id),
    refetchInterval: 10000,
  });

  const timelineQuery = useQuery({
    queryKey: ["order-timeline", id],
    queryFn: () => getOrderTimeline(id as string),
    enabled: Boolean(id),
    retry: false,
  });

  const sagaQuery = useQuery({
    queryKey: ["saga-by-order", id],
    queryFn: () => getSagaByOrderId(id as string),
    enabled: Boolean(id),
    retry: false,
  });

  const paymentsQuery = useQuery({
    queryKey: ["payments-by-order", id],
    queryFn: () => listPaymentsByOrder(id as string),
    enabled: Boolean(id),
    retry: false,
  });

  const shipmentsQuery = useQuery({
    queryKey: ["shipments-by-order", id],
    queryFn: () => listShipmentsByOrder(id as string),
    enabled: Boolean(id),
    retry: false,
  });

  const invoiceQuery = useQuery({
    queryKey: ["invoice-by-order", id],
    queryFn: () => getInvoiceByOrder(id as string),
    enabled: Boolean(id),
    retry: false,
    refetchInterval: (query) => (query.state.data ? false : 4000),
  });

  const downloadInvoice = useMutation({
    mutationFn: async () => {
      const invoice = invoiceQuery.data;
      if (!invoice) {
        throw new Error("Invoice not ready yet");
      }
      await downloadInvoicePdf(invoice.id, `${invoice.invoiceNumber}.pdf`);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(id as string, cancelReason || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["order", id] });
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      setShowCancelForm(false);
      setCancelReason("");
    },
  });

  const advanceShipmentMutation = useMutation({
    mutationFn: (shipmentId: number) => advanceShipment(shipmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shipments-by-order", id] });
      queryClient.invalidateQueries({ queryKey: ["order-timeline", id] });
      queryClient.invalidateQueries({ queryKey: ["order", id] });
    },
  });

  const createShipmentMutation = useMutation({
    mutationFn: () => createShipment(id as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shipments-by-order", id] });
      queryClient.invalidateQueries({ queryKey: ["order", id] });
      queryClient.invalidateQueries({ queryKey: ["saga-by-order", id] });
      queryClient.invalidateQueries({ queryKey: ["order-timeline", id] });
      queryClient.invalidateQueries({ queryKey: ["invoice-by-order", id] });
    },
  });

  if (orderQuery.isLoading) {
    return <div className="loading-state">Loading order…</div>;
  }

  if (orderQuery.isError || !orderQuery.data) {
    return (
      <div>
        <Link to="/" className="back-link">
          ← Back to orders
        </Link>
        <div className="error-banner">
          Failed to load order: {(orderQuery.error as Error)?.message ?? "Not found"}
        </div>
      </div>
    );
  }

  const order = orderQuery.data;
  const saga = isNotFound(sagaQuery.error) ? null : sagaQuery.data;
  const payments = paymentsQuery.data ?? [];
  const shipments = shipmentsQuery.data ?? [];
  const invoice = isNotFound(invoiceQuery.error) ? null : invoiceQuery.data ?? null;

  const timelineUnavailable = timelineQuery.isError;
  const timeline: TimelineEntry[] = timelineUnavailable
    ? buildFallbackTimeline(order, saga, payments, shipments)
    : Array.isArray(timelineQuery.data)
      ? timelineQuery.data
      : [];

  const canCancel = CANCELLABLE_STATUSES.has(order.status);

  return (
    <div>
      <Link to="/" className="back-link">
        ← Back to orders
      </Link>

      <div className="page-header">
        <div>
          <h1 className="mono">{shortId(order.id, 20)}</h1>
          <div className="page-header__subtitle">Order for {order.customerId}</div>
        </div>
        <div className="page-actions">
          <StatusBadge status={order.status} kind="order" />
          <button
            className="btn btn-danger"
            disabled={!canCancel || cancelMutation.isPending}
            onClick={() => setShowCancelForm((v) => !v)}
          >
            Cancel order
          </button>
        </div>
      </div>

      {showCancelForm && (
        <div className="panel">
          <h2>Cancel this order?</h2>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <input
              className="input"
              style={{ width: 280 }}
              placeholder="Reason (optional)"
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
            />
            <button
              className="btn btn-danger"
              disabled={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate()}
            >
              {cancelMutation.isPending ? "Cancelling…" : "Confirm cancel"}
            </button>
            <button className="btn" onClick={() => setShowCancelForm(false)}>
              Dismiss
            </button>
          </div>
          {cancelMutation.isError && (
            <div className="error-banner" style={{ marginTop: 12, marginBottom: 0 }}>
              {(cancelMutation.error as Error).message}
            </div>
          )}
        </div>
      )}

      <div className="panel-grid">
        <div className="panel">
          <h2>Order details</h2>
          <dl className="field-list">
            <dt>Order ID</dt>
            <dd className="mono">{order.id}</dd>
            <dt>Customer</dt>
            <dd>{order.customerId}</dd>
            <dt>Total</dt>
            <dd>{formatMoney(order.totalAmount, order.currency)}</dd>
            <dt>Currency</dt>
            <dd>{order.currency}</dd>
            <dt>Idempotency key</dt>
            <dd className="mono">{order.idempotencyKey ?? "—"}</dd>
            <dt>Created</dt>
            <dd>{formatDateTime(order.createdAt)}</dd>
            <dt>Updated</dt>
            <dd>{formatDateTime(order.updatedAt)}</dd>
          </dl>
        </div>

        <div className="panel">
          <h2>Lines</h2>
          <table className="data-table" style={{ margin: -4 }}>
            <thead>
              <tr>
                <th>SKU</th>
                <th>Qty</th>
                <th>Unit price</th>
              </tr>
            </thead>
            <tbody>
              {order.lines.map((line, idx) => (
                <tr key={`${line.sku}-${idx}`}>
                  <td className="mono-cell">{line.sku}</td>
                  <td>{line.quantity}</td>
                  <td className="col-num">{formatMoney(line.unitPrice, order.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {saga && (
        <div className="panel">
          <h2>Saga</h2>
          <dl className="field-list">
            <dt>Saga ID</dt>
            <dd className="mono">{saga.id}</dd>
            <dt>Status</dt>
            <dd>
              <StatusBadge status={saga.status} kind="saga" />
            </dd>
            <dt>Current step</dt>
            <dd>{saga.currentStep ?? "—"}</dd>
            <dt>Retries</dt>
            <dd>{saga.retryCount}</dd>
            {saga.lastError && (
              <>
                <dt>Last error</dt>
                <dd style={{ color: "var(--danger)" }}>{saga.lastError}</dd>
              </>
            )}
          </dl>
        </div>
      )}

      {payments.length > 0 && (
        <div className="panel">
          <h2>Payments</h2>
          <table className="data-table" style={{ margin: -4 }}>
            <thead>
              <tr>
                <th>ID</th>
                <th>Status</th>
                <th>Amount</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((payment) => (
                <tr key={payment.id}>
                  <td className="mono-cell">{payment.id}</td>
                  <td>
                    <StatusBadge status={payment.status} />
                  </td>
                  <td className="col-num">{formatMoney(payment.amount, payment.currency)}</td>
                  <td className="mono-cell">{formatDateTime(payment.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {shipments.length > 0 ? (
        <div className="panel">
          <h2>Shipments</h2>
          <table className="data-table" style={{ margin: -4 }}>
            <thead>
              <tr>
                <th>ID</th>
                <th>Status</th>
                <th>Carrier</th>
                <th>Tracking</th>
                <th>Label</th>
                <th>Created</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {shipments.map((shipment) => (
                <tr key={shipment.id}>
                  <td className="mono-cell">{shipment.id}</td>
                  <td>
                    <StatusBadge status={shipment.status} />
                  </td>
                  <td className="mono-cell">{shipment.carrier ?? "—"}</td>
                  <td className="mono-cell">{shipment.trackingNumber ?? "—"}</td>
                  <td>
                    {shipment.labelUrl ? (
                      <a href={shipment.labelUrl} target="_blank" rel="noreferrer">
                        Label
                      </a>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="mono-cell">{formatDateTime(shipment.createdAt)}</td>
                  <td>
                    {(() => {
                      const next = nextShipmentStatus(shipment.status);
                      if (TERMINAL_SHIPMENT.has(shipment.status) || !next) {
                        return null;
                      }
                      return (
                        <button
                          className="btn"
                          type="button"
                          disabled={advanceShipmentMutation.isPending}
                          onClick={() => advanceShipmentMutation.mutate(shipment.id)}
                          title={`Advance tracking to ${next.replace(/_/g, " ")}`}
                        >
                          Advance → {next.replace(/_/g, " ")}
                        </button>
                      );
                    })()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {shipments.map((shipment) =>
            shipment.events && shipment.events.length > 0 ? (
              <div key={`events-${shipment.id}`} style={{ marginTop: 16 }}>
                <h3 style={{ marginBottom: 8 }}>Tracking history #{shipment.id}</h3>
                <ol className="timeline" style={{ margin: 0 }}>
                  {shipment.events.map((event) => (
                    <li key={event.id}>
                      <div className="timeline-time">{formatDateTime(event.occurredAt)}</div>
                      <div className="timeline-label">{event.status.replace(/_/g, " ")}</div>
                      {event.message ? <div className="timeline-detail">{event.message}</div> : null}
                    </li>
                  ))}
                </ol>
              </div>
            ) : null,
          )}
        </div>
      ) : order.status === "PAID" ? (
        <div className="panel">
          <h2>Shipments</h2>
          <p className="muted" style={{ marginBottom: 12 }}>
            Awaiting fulfillment — create a shipment to book a label.
          </p>
          {createShipmentMutation.isError && (
            <div className="error-banner" style={{ marginBottom: 12 }}>
              {(createShipmentMutation.error as Error)?.message ?? "Create shipment failed"}
            </div>
          )}
          <button
            className="btn"
            type="button"
            disabled={createShipmentMutation.isPending}
            onClick={() => createShipmentMutation.mutate()}
          >
            {createShipmentMutation.isPending ? "Creating…" : "Create shipment"}
          </button>
        </div>
      ) : null}

      <div className="panel">
        <h2>Invoice</h2>
        {invoice ? (
          <dl className="field-list">
            <dt>Number</dt>
            <dd className="mono">{invoice.invoiceNumber}</dd>
            <dt>Total</dt>
            <dd>{formatMoney(invoice.total, invoice.currency)}</dd>
            <dt>GST</dt>
            <dd>
              CGST {formatMoney(invoice.cgst, invoice.currency)} · SGST{" "}
              {formatMoney(invoice.sgst, invoice.currency)} · IGST{" "}
              {formatMoney(invoice.igst, invoice.currency)}
            </dd>
            <dt>Issued</dt>
            <dd>{formatDateTime(invoice.createdAt)}</dd>
            <dt>PDF</dt>
            <dd>
              <button
                className="btn"
                disabled={downloadInvoice.isPending}
                onClick={() => downloadInvoice.mutate()}
              >
                {downloadInvoice.isPending ? "Downloading…" : "Download PDF"}
              </button>
              {downloadInvoice.isError && (
                <div className="error-banner" style={{ marginTop: 8, marginBottom: 0 }}>
                  {(downloadInvoice.error as Error).message}
                </div>
              )}
            </dd>
          </dl>
        ) : (
          <div className="empty-state">
            No invoice yet — issued automatically after shipment is created.
          </div>
        )}
      </div>

      <div className="panel">
        <h2>
          Timeline
          {timelineUnavailable && <span className="badge badge-neutral">fallback</span>}
        </h2>
        {timeline.length === 0 ? (
          <div className="empty-state">No timeline events yet.</div>
        ) : (
          <div className="timeline">
            {timeline.map((entry, idx) => (
              <div className="timeline-item" key={`${entry.timestamp}-${idx}`}>
                <div className="timeline-dot" />
                <div>
                  <div className="timeline-content__meta">
                    <span className="timeline-content__label">{entry.label}</span>
                    <span className="timeline-content__time">{formatDateTime(entry.timestamp)}</span>
                  </div>
                  {entry.detail && <div className="timeline-content__detail">{entry.detail}</div>}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
