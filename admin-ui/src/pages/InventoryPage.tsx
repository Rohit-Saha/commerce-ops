import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createProduct,
  deleteProduct,
  listInventory,
  restock,
  updateProduct,
} from "../api/inventory";
import { formatMoney } from "../lib/format";
import type { StockItem } from "../api/types";

interface CreateFormState {
  sku: string;
  name: string;
  unitPrice: string;
  availableQty: string;
}

interface EditFormState {
  name: string;
  unitPrice: string;
}

const EMPTY_CREATE: CreateFormState = {
  sku: "",
  name: "",
  unitPrice: "0.00",
  availableQty: "0",
};

export function InventoryPage() {
  const queryClient = useQueryClient();
  const [qtyBySku, setQtyBySku] = useState<Record<string, number>>({});
  const [createForm, setCreateForm] = useState<CreateFormState>(EMPTY_CREATE);
  const [editingSku, setEditingSku] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<EditFormState>({ name: "", unitPrice: "0.00" });
  const [actionError, setActionError] = useState<string | null>(null);

  const inventoryQuery = useQuery({
    queryKey: ["inventory"],
    queryFn: listInventory,
    refetchInterval: 15000,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["inventory"] });

  const createMutation = useMutation({
    mutationFn: createProduct,
    onSuccess: () => {
      setCreateForm(EMPTY_CREATE);
      setActionError(null);
      invalidate();
    },
    onError: (error: Error) => setActionError(error.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ sku, name, unitPrice }: { sku: string; name: string; unitPrice: number }) =>
      updateProduct(sku, { name, unitPrice }),
    onSuccess: () => {
      setEditingSku(null);
      setActionError(null);
      invalidate();
    },
    onError: (error: Error) => setActionError(error.message),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteProduct,
    onSuccess: () => {
      setActionError(null);
      invalidate();
    },
    onError: (error: Error) => setActionError(error.message),
  });

  const restockMutation = useMutation({
    mutationFn: ({ sku, qty }: { sku: string; qty: number }) => restock(sku, qty),
    onSuccess: () => {
      setActionError(null);
      invalidate();
    },
    onError: (error: Error) => setActionError(error.message),
  });

  const items = inventoryQuery.data ?? [];
  const busy =
    createMutation.isPending ||
    updateMutation.isPending ||
    deleteMutation.isPending ||
    restockMutation.isPending;

  function startEdit(item: StockItem) {
    setEditingSku(item.sku);
    setEditForm({
      name: item.name,
      unitPrice: Number(item.unitPrice).toFixed(2),
    });
    setActionError(null);
  }

  function submitCreate() {
    const unitPrice = Number(createForm.unitPrice);
    const availableQty = Number(createForm.availableQty);
    if (!createForm.sku.trim() || !createForm.name.trim()) {
      setActionError("SKU and name are required");
      return;
    }
    if (Number.isNaN(unitPrice) || unitPrice < 0) {
      setActionError("Unit price must be a non-negative number");
      return;
    }
    if (Number.isNaN(availableQty) || availableQty < 0 || !Number.isInteger(availableQty)) {
      setActionError("Initial available qty must be a non-negative integer");
      return;
    }
    createMutation.mutate({
      sku: createForm.sku.trim(),
      name: createForm.name.trim(),
      unitPrice,
      availableQty,
    });
  }

  function submitEdit(sku: string) {
    const unitPrice = Number(editForm.unitPrice);
    if (!editForm.name.trim()) {
      setActionError("Name is required");
      return;
    }
    if (Number.isNaN(unitPrice) || unitPrice < 0) {
      setActionError("Unit price must be a non-negative number");
      return;
    }
    updateMutation.mutate({ sku, name: editForm.name.trim(), unitPrice });
  }

  function confirmDelete(item: StockItem) {
    if (item.reservedQty > 0) {
      setActionError(`Cannot delete ${item.sku}: reserved qty is ${item.reservedQty}`);
      return;
    }
    if (!window.confirm(`Soft-delete product ${item.sku} (${item.name})?`)) {
      return;
    }
    deleteMutation.mutate(item.sku);
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Inventory</h1>
          <div className="page-header__subtitle">
            {items.length} SKU{items.length === 1 ? "" : "s"} tracked
          </div>
        </div>
      </div>

      <div className="panel">
        <h2>Add product</h2>
        <div className="form-row">
          <label className="form-field">
            <span>SKU</span>
            <input
              className="input input-wide"
              value={createForm.sku}
              onChange={(e) => setCreateForm((f) => ({ ...f, sku: e.target.value }))}
              placeholder="SKU-NEW-001"
            />
          </label>
          <label className="form-field">
            <span>Name</span>
            <input
              className="input input-wide"
              value={createForm.name}
              onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))}
              placeholder="Product name"
            />
          </label>
          <label className="form-field">
            <span>Unit price</span>
            <input
              className="input"
              type="number"
              min={0}
              step="0.01"
              value={createForm.unitPrice}
              onChange={(e) => setCreateForm((f) => ({ ...f, unitPrice: e.target.value }))}
            />
          </label>
          <label className="form-field">
            <span>Initial qty</span>
            <input
              className="input"
              type="number"
              min={0}
              step={1}
              value={createForm.availableQty}
              onChange={(e) => setCreateForm((f) => ({ ...f, availableQty: e.target.value }))}
            />
          </label>
          <button className="btn btn-primary" disabled={busy} onClick={submitCreate}>
            {createMutation.isPending ? "Creating…" : "Create"}
          </button>
        </div>
      </div>

      {inventoryQuery.isError && (
        <div className="error-banner">
          Failed to load inventory: {(inventoryQuery.error as Error).message}
        </div>
      )}
      {actionError && <div className="error-banner">{actionError}</div>}

      <div className="table-wrap">
        {inventoryQuery.isLoading ? (
          <div className="loading-state">Loading inventory…</div>
        ) : items.length === 0 ? (
          <div className="empty-state">No stock items found. Add a product above.</div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>SKU</th>
                <th>Name</th>
                <th>Unit price</th>
                <th>Available</th>
                <th>Reserved</th>
                <th>Restock</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const qty = qtyBySku[item.sku] ?? 10;
                const isRestocking =
                  restockMutation.isPending && restockMutation.variables?.sku === item.sku;
                const isEditing = editingSku === item.sku;
                const isDeleting =
                  deleteMutation.isPending && deleteMutation.variables === item.sku;
                const isUpdating =
                  updateMutation.isPending && updateMutation.variables?.sku === item.sku;

                return (
                  <tr key={item.sku}>
                    <td className="id-cell">{item.sku}</td>
                    <td>
                      {isEditing ? (
                        <input
                          className="input input-wide"
                          value={editForm.name}
                          onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))}
                        />
                      ) : (
                        item.name
                      )}
                    </td>
                    <td className="col-num">
                      {isEditing ? (
                        <input
                          className="input"
                          type="number"
                          min={0}
                          step="0.01"
                          value={editForm.unitPrice}
                          onChange={(e) =>
                            setEditForm((f) => ({ ...f, unitPrice: e.target.value }))
                          }
                        />
                      ) : (
                        formatMoney(Number(item.unitPrice))
                      )}
                    </td>
                    <td className="col-num">{item.availableQty}</td>
                    <td className="col-num">{item.reservedQty}</td>
                    <td>
                      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                        <input
                          type="number"
                          min={1}
                          className="input"
                          value={qty}
                          disabled={isEditing}
                          onChange={(e) =>
                            setQtyBySku((prev) => ({
                              ...prev,
                              [item.sku]: Number(e.target.value) || 1,
                            }))
                          }
                        />
                        <button
                          className="btn btn-sm"
                          disabled={busy || isEditing}
                          onClick={() => restockMutation.mutate({ sku: item.sku, qty })}
                        >
                          {isRestocking ? "Restocking…" : "Restock"}
                        </button>
                      </div>
                    </td>
                    <td>
                      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                        {isEditing ? (
                          <>
                            <button
                              className="btn btn-sm btn-primary"
                              disabled={busy}
                              onClick={() => submitEdit(item.sku)}
                            >
                              {isUpdating ? "Saving…" : "Save"}
                            </button>
                            <button
                              className="btn btn-sm"
                              disabled={busy}
                              onClick={() => setEditingSku(null)}
                            >
                              Cancel
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              className="btn btn-sm"
                              disabled={busy}
                              onClick={() => startEdit(item)}
                            >
                              Edit
                            </button>
                            <button
                              className="btn btn-sm btn-danger"
                              disabled={busy}
                              onClick={() => confirmDelete(item)}
                            >
                              {isDeleting ? "Deleting…" : "Delete"}
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
