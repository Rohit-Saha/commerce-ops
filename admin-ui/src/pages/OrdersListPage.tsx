import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listOrders } from "../api/orders";
import { StatusBadge } from "../components/StatusBadge";
import { shortId, formatMoney, formatDateTime } from "../lib/format";
import { useConnectOrdersStream } from "../hooks/useOrdersStream";
import type { Order, OrderStatus } from "../api/types";

const ORDER_STATUSES: OrderStatus[] = [
  "PENDING",
  "RESERVING",
  "RESERVED",
  "PAYMENT_PENDING",
  "PAID",
  "SHIPPING",
  "DELIVERED",
  "COMPLETED",
  "CANCELLED",
  "FAILED",
];

type SortKey = "createdAt" | "updatedAt" | "totalAmount" | "status" | "customerId" | "id";
type SortDir = "asc" | "desc";

function compareOrders(a: Order, b: Order, key: SortKey, dir: SortDir): number {
  const mul = dir === "asc" ? 1 : -1;
  switch (key) {
    case "createdAt":
    case "updatedAt": {
      const ta = new Date(a[key]).getTime();
      const tb = new Date(b[key]).getTime();
      return (ta - tb) * mul;
    }
    case "totalAmount":
      return (a.totalAmount - b.totalAmount) * mul;
    case "status":
    case "customerId":
    case "id":
      return a[key].localeCompare(b[key], undefined, { sensitivity: "base" }) * mul;
    default:
      return 0;
  }
}

function SortHeader({
  label,
  column,
  sortKey,
  sortDir,
  onSort,
}: {
  label: string;
  column: SortKey;
  sortKey: SortKey;
  sortDir: SortDir;
  onSort: (key: SortKey) => void;
}) {
  const active = sortKey === column;
  return (
    <th>
      <button
        type="button"
        className={`th-sort${active ? " th-sort--active" : ""}`}
        onClick={() => onSort(column)}
      >
        {label}
        <span className="th-sort__ind" aria-hidden>
          {active ? (sortDir === "asc" ? " ↑" : " ↓") : ""}
        </span>
      </button>
    </th>
  );
}

export function OrdersListPage() {
  useConnectOrdersStream();
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [query, setQuery] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("createdAt");
  const [sortDir, setSortDir] = useState<SortDir>("desc");

  const ordersQuery = useQuery({
    queryKey: ["orders"],
    queryFn: listOrders,
    refetchInterval: 15000,
  });

  const orders = ordersQuery.data ?? [];

  const filteredSorted = useMemo(() => {
    const q = query.trim().toLowerCase();
    const filtered = orders.filter((order) => {
      if (statusFilter !== "ALL" && order.status !== statusFilter) {
        return false;
      }
      if (!q) {
        return true;
      }
      return (
        order.id.toLowerCase().includes(q)
        || order.customerId.toLowerCase().includes(q)
        || order.status.toLowerCase().includes(q)
        || order.currency.toLowerCase().includes(q)
      );
    });
    return [...filtered].sort((a, b) => compareOrders(a, b, sortKey, sortDir));
  }, [orders, statusFilter, query, sortKey, sortDir]);

  const onSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir(key === "createdAt" || key === "updatedAt" || key === "totalAmount" ? "desc" : "asc");
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Orders</h1>
          <div className="page-header__subtitle">
            {filteredSorted.length}
            {filteredSorted.length !== orders.length ? ` of ${orders.length}` : ""} order
            {filteredSorted.length === 1 ? "" : "s"}
          </div>
        </div>
        <div className="page-actions">
          <button className="btn btn-primary" onClick={() => navigate("/demo")}>
            Run demo flow
          </button>
        </div>
      </div>

      {ordersQuery.isError && (
        <div className="error-banner">Failed to load orders: {(ordersQuery.error as Error).message}</div>
      )}

      <div className="list-toolbar">
        <label className="list-toolbar__field">
          <span className="list-toolbar__label">Search</span>
          <input
            className="input"
            type="search"
            placeholder="Order id, customer…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        <label className="list-toolbar__field">
          <span className="list-toolbar__label">Status</span>
          <select
            className="input"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">All statuses</option>
            {ORDER_STATUSES.map((status) => (
              <option key={status} value={status}>
                {status.replace(/_/g, " ")}
              </option>
            ))}
          </select>
        </label>
        <label className="list-toolbar__field">
          <span className="list-toolbar__label">Sort</span>
          <select
            className="input"
            value={`${sortKey}:${sortDir}`}
            onChange={(e) => {
              const [key, dir] = e.target.value.split(":") as [SortKey, SortDir];
              setSortKey(key);
              setSortDir(dir);
            }}
          >
            <option value="createdAt:desc">Created (newest)</option>
            <option value="createdAt:asc">Created (oldest)</option>
            <option value="updatedAt:desc">Updated (newest)</option>
            <option value="updatedAt:asc">Updated (oldest)</option>
            <option value="totalAmount:desc">Total (high → low)</option>
            <option value="totalAmount:asc">Total (low → high)</option>
            <option value="status:asc">Status (A → Z)</option>
            <option value="customerId:asc">Customer (A → Z)</option>
          </select>
        </label>
        {(statusFilter !== "ALL" || query.trim() || sortKey !== "createdAt" || sortDir !== "desc") && (
          <button
            type="button"
            className="btn btn-sm"
            onClick={() => {
              setStatusFilter("ALL");
              setQuery("");
              setSortKey("createdAt");
              setSortDir("desc");
            }}
          >
            Reset
          </button>
        )}
      </div>

      <div className="table-wrap">
        {ordersQuery.isLoading ? (
          <div className="loading-state">Loading orders…</div>
        ) : orders.length === 0 ? (
          <div className="empty-state">
            No orders yet.{" "}
            <button className="btn btn-sm btn-primary" onClick={() => navigate("/demo")}>
              Run a demo flow
            </button>
          </div>
        ) : filteredSorted.length === 0 ? (
          <div className="empty-state">No orders match the current filters.</div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <SortHeader label="ID" column="id" sortKey={sortKey} sortDir={sortDir} onSort={onSort} />
                <SortHeader
                  label="Customer"
                  column="customerId"
                  sortKey={sortKey}
                  sortDir={sortDir}
                  onSort={onSort}
                />
                <SortHeader
                  label="Status"
                  column="status"
                  sortKey={sortKey}
                  sortDir={sortDir}
                  onSort={onSort}
                />
                <SortHeader
                  label="Total"
                  column="totalAmount"
                  sortKey={sortKey}
                  sortDir={sortDir}
                  onSort={onSort}
                />
                <SortHeader
                  label="Created"
                  column="createdAt"
                  sortKey={sortKey}
                  sortDir={sortDir}
                  onSort={onSort}
                />
              </tr>
            </thead>
            <tbody>
              {filteredSorted.map((order: Order) => (
                <tr key={order.id} className="clickable" onClick={() => navigate(`/orders/${order.id}`)}>
                  <td className="id-cell">{shortId(order.id)}</td>
                  <td>{order.customerId}</td>
                  <td>
                    <StatusBadge status={order.status} kind="order" />
                  </td>
                  <td className="col-num">{formatMoney(order.totalAmount, order.currency)}</td>
                  <td className="mono-cell">{formatDateTime(order.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
