import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cancelOrder, getOrder } from "../api/orders";
import { downloadInvoicePdf, getInvoiceByOrder } from "../api/invoices";
import { listShipmentsByOrder } from "../api/shipments";
import { ApiError } from "../api/client";
import { LoadingState, Spinner } from "../components/Spinner";
import { formatDateTime, formatMoney, shortId } from "../lib/format";
import { useNotify, useNotifyQueryError } from "../lib/notify";

const TERMINAL = new Set(["DELIVERED", "COMPLETED", "FAILED", "CANCELLED"]);
const HAS_SHIPMENT = new Set(["SHIPPING", "DELIVERED", "COMPLETED"]);
const CAN_CANCEL = new Set(["PENDING", "RESERVING", "RESERVED", "PAYMENT_PENDING", "PAID"]);
const TERMINAL_SHIPMENT = new Set(["DELIVERED", "FAILED", "RTO", "CANCELLED"]);

export function OrderStatusPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { notifyError } = useNotify();

  const orderQuery = useQuery({
    queryKey: ["order", id],
    queryFn: () => getOrder(id as string),
    enabled: Boolean(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status && TERMINAL.has(status)) {
        return false;
      }
      return 2500;
    },
  });
  useNotifyQueryError(orderQuery.isError, orderQuery.error, "order");

  const invoiceQuery = useQuery({
    queryKey: ["invoice", id],
    queryFn: () => getInvoiceByOrder(id as string),
    enabled: Boolean(id) && HAS_SHIPMENT.has(orderQuery.data?.status ?? ""),
    retry: (count, error) => {
      if (error instanceof ApiError && error.status === 404) {
        return count < 8;
      }
      return false;
    },
    refetchInterval: (query) => (query.state.data ? false : 3000),
  });

  const shipmentsQuery = useQuery({
    queryKey: ["shipments", id],
    queryFn: () => listShipmentsByOrder(id as string),
    enabled: Boolean(id) && HAS_SHIPMENT.has(orderQuery.data?.status ?? ""),
    refetchInterval: (query) => {
      const shipment = query.state.data?.[0];
      if (shipment && TERMINAL_SHIPMENT.has(shipment.status)) {
        return false;
      }
      return 5000;
    },
  });

  const download = useMutation({
    mutationFn: async () => {
      const invoice = invoiceQuery.data;
      if (!invoice) {
        throw new Error("Invoice not ready yet");
      }
      await downloadInvoicePdf(invoice.id, `${invoice.invoiceNumber}.pdf`);
    },
    onError: (err: Error) => notifyError(err, "invoice"),
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(id as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["order", id] });
      queryClient.invalidateQueries({ queryKey: ["orders", "mine"] });
    },
    onError: (err: Error) => notifyError(err, "order"),
  });

  if (orderQuery.isLoading) {
    return <LoadingState className="page-narrow loading-state--page" label="Loading order…" />;
  }

  if (orderQuery.isError || !orderQuery.data) {
    return (
      <div className="page-narrow">
        <div className="empty">This order couldn’t be loaded.</div>
        <Link to="/catalog">Back to shop</Link>
      </div>
    );
  }

  const order = orderQuery.data;
  const terminal = TERMINAL.has(order.status);
  const ship = order.shippingAddress;
  const invoice = invoiceQuery.data;
  const shipment = shipmentsQuery.data?.[0];
  const canCancel = CAN_CANCEL.has(order.status);

  return (
    <div className="page-narrow">
      <p className="eyebrow">Order placed</p>
      <h1 className="mono">{shortId(order.id, 18)}</h1>
      <p className="lede">
        {order.status === "PAID"
          ? "Payment received — awaiting shipment."
          : order.status === "SHIPPING"
            ? "Your order is on its way."
            : order.status === "DELIVERED"
              ? "Delivered."
              : terminal
                ? "Order update."
                : "Status updates as fulfillment runs — refreshing automatically…"}
      </p>

      <div className={`status-pill status-${order.status.toLowerCase()}`}>{order.status.replace(/_/g, " ")}</div>

      <dl className="detail-list">
        <dt>Total</dt>
        <dd>{formatMoney(order.totalAmount, order.currency)}</dd>
        <dt>Created</dt>
        <dd>{formatDateTime(order.createdAt)}</dd>
        <dt>Updated</dt>
        <dd>{formatDateTime(order.updatedAt)}</dd>
        {ship && (
          <>
            <dt>Ship to</dt>
            <dd>
              {ship.recipientName}
              <br />
              {ship.line1}
              {ship.line2 ? (
                <>
                  <br />
                  {ship.line2}
                </>
              ) : null}
              <br />
              {ship.city}, {ship.state} {ship.postalCode}
              <br />
              {ship.country}
            </dd>
          </>
        )}
        {shipment && (
          <>
            <dt>Shipment</dt>
            <dd>
              <div className={`status-pill status-${shipment.status.toLowerCase()}`}>
                {shipment.status.replace(/_/g, " ")}
              </div>
              {shipment.trackingNumber ? (
                <div className="mono" style={{ marginTop: 8 }}>
                  Tracking: {shipment.trackingNumber}
                </div>
              ) : null}
              {shipment.carrier ? <div className="muted">Carrier: {shipment.carrier}</div> : null}
            </dd>
          </>
        )}
        {invoice && (
          <>
            <dt>Invoice</dt>
            <dd className="mono">{invoice.invoiceNumber}</dd>
          </>
        )}
      </dl>

      {shipment?.events && shipment.events.length > 0 ? (
        <>
          <h2>Tracking history</h2>
          <div className="checkout-lines">
            {shipment.events.map((event) => (
              <div className="checkout-line" key={event.id}>
                <span>
                  {event.status.replace(/_/g, " ")}
                  {event.message ? ` — ${event.message}` : ""}
                </span>
                <span className="muted">{formatDateTime(event.occurredAt)}</span>
              </div>
            ))}
          </div>
        </>
      ) : null}

      <h2>Lines</h2>
      <div className="checkout-lines">
        {order.lines.map((line, idx) => (
          <div className="checkout-line" key={`${line.sku}-${idx}`}>
            <span>
              {line.sku} × {line.quantity}
            </span>
            <span>{formatMoney(line.unitPrice * line.quantity, order.currency)}</span>
          </div>
        ))}
      </div>

      <div className="checkout-actions" style={{ marginTop: 28 }}>
        <Link className="btn btn-ghost" to="/account/orders">
          Order history
        </Link>
        {canCancel ? (
          <button
            className="btn btn-ghost"
            type="button"
            disabled={cancelMutation.isPending}
            onClick={() => {
              if (window.confirm("Cancel this order? Payment will be refunded if already captured.")) {
                cancelMutation.mutate();
              }
            }}
          >
            {cancelMutation.isPending ? <Spinner size="sm" label="Cancelling" /> : null}
            {cancelMutation.isPending ? "Cancelling…" : "Cancel order"}
          </button>
        ) : null}
        {invoice ? (
          <button
            className="btn btn-primary"
            disabled={download.isPending}
            onClick={() => download.mutate()}
          >
            {download.isPending ? <Spinner size="sm" label="Downloading" /> : null}
            {download.isPending ? "Downloading…" : "Download invoice"}
          </button>
        ) : (
          <Link className="btn btn-primary" to="/catalog">
            Continue shopping
          </Link>
        )}
      </div>
      {HAS_SHIPMENT.has(order.status) && !invoice && (
        <p className="checkout-hint" style={{ marginTop: 12 }}>
          Tax invoice is being prepared…
        </p>
      )}
    </div>
  );
}
