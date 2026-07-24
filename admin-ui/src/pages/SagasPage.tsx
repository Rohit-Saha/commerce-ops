import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listSagas } from "../api/sagas";
import { StatusBadge } from "../components/StatusBadge";
import { formatDateTime, shortId } from "../lib/format";

export function SagasPage() {
  const navigate = useNavigate();

  const sagasQuery = useQuery({
    queryKey: ["sagas"],
    queryFn: listSagas,
    refetchInterval: 10000,
  });

  const sagas = sagasQuery.data ?? [];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Sagas</h1>
          <div className="page-header__subtitle">{sagas.length} saga instance{sagas.length === 1 ? "" : "s"}</div>
        </div>
      </div>

      {sagasQuery.isError && (
        <div className="error-banner">Failed to load sagas: {(sagasQuery.error as Error).message}</div>
      )}

      <div className="table-wrap">
        {sagasQuery.isLoading ? (
          <div className="loading-state">Loading sagas…</div>
        ) : sagas.length === 0 ? (
          <div className="empty-state">No sagas found.</div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Saga ID</th>
                <th>Order</th>
                <th>Status</th>
                <th>Current step</th>
                <th>Retries</th>
                <th>Updated</th>
              </tr>
            </thead>
            <tbody>
              {sagas.map((saga) => (
                <tr key={saga.id} className="clickable" onClick={() => navigate(`/orders/${saga.orderId}`)}>
                  <td className="mono-cell">{saga.id}</td>
                  <td className="id-cell">{shortId(saga.orderId)}</td>
                  <td>
                    <StatusBadge status={saga.status} kind="saga" />
                  </td>
                  <td>{saga.currentStep ?? "—"}</td>
                  <td className="col-num">{saga.retryCount}</td>
                  <td className="mono-cell">{formatDateTime(saga.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
