import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listMyOrders } from "../api/orders";
import { formatDateTime, formatMoney, shortId } from "../lib/format";
import { useNotifyQueryError } from "../lib/notify";
import { LoadingState } from "../components/Spinner";

export function OrdersPage() {
  const ordersQuery = useQuery({
    queryKey: ["my-orders"],
    queryFn: listMyOrders,
  });
  useNotifyQueryError(ordersQuery.isError, ordersQuery.error, "orders");

  if (ordersQuery.isLoading) {
    return (
      <div className="account-panel">
        <LoadingState label="Loading orders…" />
      </div>
    );
  }

  if (ordersQuery.isError) {
    return (
      <div className="account-panel">
        <h2>Your orders</h2>
        <div className="empty">Orders couldn’t be loaded. Dismiss the banner and refresh to try again.</div>
      </div>
    );
  }

  const orders = ordersQuery.data ?? [];

  return (
    <div className="account-panel">
      <h2>Your orders</h2>
      <p className="lede" style={{ marginTop: 0 }}>
        Order history for your account.
      </p>

      {orders.length === 0 ? (
        <div className="empty">
          No orders yet. <Link to="/catalog">Browse the catalog</Link>.
        </div>
      ) : (
        <div className="order-history">
          {orders.map((order) => (
            <Link className="order-history__row" key={order.id} to={`/orders/${order.id}`}>
              <div>
                <div className="mono">{shortId(order.id, 18)}</div>
                <div className="muted">{formatDateTime(order.createdAt)}</div>
              </div>
              <div className={`status-pill status-${order.status.toLowerCase()}`}>
                {order.status.replace(/_/g, " ")}
              </div>
              <div>{formatMoney(order.totalAmount, order.currency)}</div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
