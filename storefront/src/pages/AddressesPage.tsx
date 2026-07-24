import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createAddress,
  deleteAddress,
  listAddresses,
  setDefaultAddress,
  updateAddress,
} from "../api/addresses";
import type { Address, AddressInput } from "../api/types";
import { LoadingState, Spinner } from "../components/Spinner";
import { useAuth } from "../lib/auth";
import { sessionCredential } from "../lib/authSession";
import { useNotify, useNotifyQueryError } from "../lib/notify";

const emptyForm: AddressInput = {
  recipientName: "",
  line1: "",
  line2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "US",
  isDefault: false,
};

export function AddressesPage() {
  const { session } = useAuth();
  const token = sessionCredential(session);
  const queryClient = useQueryClient();
  const { notifyError } = useNotify();
  const [form, setForm] = useState<AddressInput>(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);

  const addressesQuery = useQuery({
    queryKey: ["addresses"],
    queryFn: () => listAddresses(token),
  });
  useNotifyQueryError(addressesQuery.isError, addressesQuery.error, "addresses");

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["addresses"] });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (editingId) {
        return updateAddress(token, editingId, form);
      }
      return createAddress(token, form);
    },
    onSuccess: async () => {
      setForm(emptyForm);
      setEditingId(null);
      await invalidate();
    },
    onError: (err: Error) => notifyError(err, "addresses"),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAddress(token, id),
    onSuccess: () => invalidate(),
    onError: (err: Error) => notifyError(err, "addresses"),
  });

  const defaultMutation = useMutation({
    mutationFn: (id: string) => setDefaultAddress(token, id),
    onSuccess: () => invalidate(),
    onError: (err: Error) => notifyError(err, "addresses"),
  });

  const sorted = useMemo(() => addressesQuery.data ?? [], [addressesQuery.data]);

  function startEdit(address: Address) {
    setEditingId(address.id);
    setForm({
      recipientName: address.recipientName,
      line1: address.line1,
      line2: address.line2 ?? "",
      city: address.city,
      state: address.state,
      postalCode: address.postalCode,
      country: address.country,
      isDefault: address.isDefault,
    });
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    saveMutation.mutate();
  }

  return (
    <div className="account-panel">
      <h2>Address book</h2>
      <p className="lede" style={{ marginTop: 0 }}>
        Save delivery addresses and mark one as default for checkout.
      </p>

      {addressesQuery.isLoading && (
        <LoadingState className="loading-state--inline" label="Loading addresses…" size="sm" />
      )}
      {addressesQuery.isError && (
        <div className="empty">Addresses couldn’t be loaded. Dismiss the banner and try again.</div>
      )}

      <div className="address-list">
        {sorted.map((address) => (
          <div className="address-card" key={address.id}>
            <div>
              <strong>{address.recipientName}</strong>
              {address.isDefault && <span className="address-default">Default</span>}
              <div className="address-lines">
                {address.line1}
                {address.line2 ? <>, {address.line2}</> : null}
                <br />
                {address.city}, {address.state} {address.postalCode}
                <br />
                {address.country}
              </div>
            </div>
            <div className="address-actions">
              <button className="btn btn-ghost" type="button" onClick={() => startEdit(address)}>
                Edit
              </button>
              {!address.isDefault && (
                <button
                  className="btn btn-ghost"
                  type="button"
                  onClick={() => defaultMutation.mutate(address.id)}
                >
                  Set default
                </button>
              )}
              <button
                className="btn btn-ghost"
                type="button"
                onClick={() => deleteMutation.mutate(address.id)}
              >
                Delete
              </button>
            </div>
          </div>
        ))}
        {!addressesQuery.isLoading && !addressesQuery.isError && sorted.length === 0 && (
          <div className="empty">No saved addresses yet.</div>
        )}
      </div>

      <h2>{editingId ? "Edit address" : "Add address"}</h2>
      <form className="auth-form" onSubmit={onSubmit}>
        <label>
          Recipient
          <input
            required
            value={form.recipientName}
            onChange={(e) => setForm({ ...form, recipientName: e.target.value })}
          />
        </label>
        <label>
          Address line 1
          <input
            required
            value={form.line1}
            onChange={(e) => setForm({ ...form, line1: e.target.value })}
          />
        </label>
        <label>
          Address line 2
          <input value={form.line2 ?? ""} onChange={(e) => setForm({ ...form, line2: e.target.value })} />
        </label>
        <div className="form-row">
          <label>
            City
            <input
              required
              value={form.city}
              onChange={(e) => setForm({ ...form, city: e.target.value })}
            />
          </label>
          <label>
            State
            <input
              required
              value={form.state}
              onChange={(e) => setForm({ ...form, state: e.target.value })}
            />
          </label>
        </div>
        <div className="form-row">
          <label>
            Postal code
            <input
              required
              value={form.postalCode}
              onChange={(e) => setForm({ ...form, postalCode: e.target.value })}
            />
          </label>
          <label>
            Country
            <input
              required
              value={form.country ?? "US"}
              onChange={(e) => setForm({ ...form, country: e.target.value })}
            />
          </label>
        </div>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={Boolean(form.isDefault)}
            onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
          />
          Set as default
        </label>
        <div className="checkout-actions">
          {editingId && (
            <button
              className="btn btn-ghost"
              type="button"
              onClick={() => {
                setEditingId(null);
                setForm(emptyForm);
              }}
            >
              Cancel
            </button>
          )}
          <button className="btn btn-primary" type="submit" disabled={saveMutation.isPending}>
            {saveMutation.isPending ? <Spinner size="sm" label="Saving" /> : null}
            {saveMutation.isPending ? "Saving…" : editingId ? "Update address" : "Save address"}
          </button>
        </div>
      </form>
    </div>
  );
}
