import { useMemo, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { listAddresses } from "../api/addresses";
import { createOrder, randomIdempotencyKey } from "../api/orders";
import { createRazorpayOrder } from "../api/payments";
import type { AddressInput, CreateOrderInput } from "../api/types";
import { clearCart, cartSubtotal } from "../lib/cart";
import { formatMoney } from "../lib/format";
import { useAuth } from "../lib/auth";
import { sessionCredential } from "../lib/authSession";
import { useNotify, useNotifyQueryError } from "../lib/notify";
import { useCart } from "../lib/useCart";
import { isBenignError } from "../lib/errors";
import { LoadingState, Spinner } from "../components/Spinner";

const emptyAddress: AddressInput = {
  recipientName: "",
  line1: "",
  line2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "IN",
  isDefault: true,
};

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => {
      open: () => void;
      on: (event: string, handler: (response: { error?: { description?: string } }) => void) => void;
    };
  }
}

function loadCheckoutScript(): Promise<void> {
  if (window.Razorpay) {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>('script[data-razorpay="checkout"]');
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("Failed to load Razorpay Checkout")));
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.dataset.razorpay = "checkout";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Failed to load Razorpay Checkout"));
    document.body.appendChild(script);
  });
}

function buildOrderPayload(
  lines: ReturnType<typeof useCart>["lines"],
  mode: "saved" | "new",
  effectiveSelectedId: string | null,
  newAddress: AddressInput,
  payment: { razorpayOrderId: string; razorpayPaymentId: string; razorpaySignature: string },
): CreateOrderInput {
  const base = {
    currency: "INR" as const,
    lines: lines.map((line) => ({
      sku: line.sku,
      quantity: line.quantity,
      unitPrice: line.unitPrice,
    })),
    ...payment,
  };
  if (mode === "saved") {
    if (!effectiveSelectedId) {
      throw new Error("Select a delivery address or add a new one");
    }
    return { ...base, shippingAddressId: effectiveSelectedId };
  }
  return {
    ...base,
    shippingAddress: {
      recipientName: newAddress.recipientName,
      line1: newAddress.line1,
      line2: newAddress.line2 || undefined,
      city: newAddress.city,
      state: newAddress.state,
      postalCode: newAddress.postalCode,
      country: newAddress.country || "IN",
      isDefault: newAddress.isDefault,
    },
  };
}

async function authorizeWithRazorpay(
  amount: number,
  customerName: string,
): Promise<{ razorpayOrderId: string; razorpayPaymentId: string; razorpaySignature: string }> {
  const session = await createRazorpayOrder(amount, "INR");
  const useLiveCheckout =
    session.provider === "razorpay" &&
    Boolean(session.keyId) &&
    !session.keyId.includes("simulated") &&
    !session.razorpayOrderId.startsWith("order_sim_");

  if (!useLiveCheckout) {
    return {
      razorpayOrderId: session.razorpayOrderId,
      razorpayPaymentId: `pay_sim_${Date.now()}`,
      razorpaySignature: "simulated",
    };
  }

  await loadCheckoutScript();
  if (!window.Razorpay) {
    throw new Error("Razorpay Checkout is unavailable");
  }

  return new Promise((resolve, reject) => {
    let settled = false;
    const rzp = new window.Razorpay!({
      key: session.keyId,
      amount: session.amountPaise,
      currency: session.currency || "INR",
      name: "Northline Goods",
      description: "Order payment",
      order_id: session.razorpayOrderId,
      prefill: { name: customerName },
      theme: { color: "#3d5a45" },
      handler(response: {
        razorpay_order_id: string;
        razorpay_payment_id: string;
        razorpay_signature: string;
      }) {
        settled = true;
        resolve({
          razorpayOrderId: response.razorpay_order_id,
          razorpayPaymentId: response.razorpay_payment_id,
          razorpaySignature: response.razorpay_signature,
        });
      },
      modal: {
        ondismiss() {
          if (!settled) {
            reject(new Error("Payment cancelled"));
          }
        },
      },
    });
    rzp.on("payment.failed", (response) => {
      settled = true;
      reject(new Error(response.error?.description || "Payment failed"));
    });
    rzp.open();
  });
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const { session } = useAuth();
  const { lines } = useCart();
  const { notifyError } = useNotify();
  const [mode, setMode] = useState<"saved" | "new">("saved");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [newAddress, setNewAddress] = useState<AddressInput>(emptyAddress);

  const addressesQuery = useQuery({
    queryKey: ["addresses"],
    queryFn: () => listAddresses(sessionCredential(session)),
  });
  useNotifyQueryError(addressesQuery.isError, addressesQuery.error, "addresses");

  const addresses = addressesQuery.data ?? [];
  const effectiveSelectedId = useMemo(() => {
    if (selectedId && addresses.some((a) => a.id === selectedId)) {
      return selectedId;
    }
    const def = addresses.find((a) => a.isDefault);
    return def?.id ?? addresses[0]?.id ?? null;
  }, [addresses, selectedId]);

  const placeOrder = useMutation({
    mutationFn: async () => {
      const subtotal = cartSubtotal(lines);
      const payment = await authorizeWithRazorpay(subtotal, session?.customer.displayName ?? "Customer");
      const payload = buildOrderPayload(lines, mode, effectiveSelectedId, newAddress, payment);
      return createOrder(payload, randomIdempotencyKey());
    },
    onSuccess: (order) => {
      clearCart();
      navigate(`/orders/${order.id}`);
    },
    onError: (err: Error) => {
      if (isBenignError(err)) return;
      notifyError(err, "checkout");
    },
  });

  if (lines.length === 0) {
    return <Navigate to="/cart" replace />;
  }

  const subtotal = cartSubtotal(lines);
  const canPlace =
    mode === "new"
      ? Boolean(
          newAddress.recipientName &&
            newAddress.line1 &&
            newAddress.city &&
            newAddress.state &&
            newAddress.postalCode,
        )
      : Boolean(effectiveSelectedId);

  const disabledReason = !canPlace
    ? mode === "saved"
      ? "Select a saved address or switch to a new one."
      : "Fill in recipient, street, city, state, and postal code."
    : null;

  return (
    <div className="checkout-page">
      <h1>Checkout</h1>
      <p className="lede">
        Signed in as <strong>{session?.customer.displayName}</strong>. Choose where we should deliver, then pay in INR.
      </p>

      <div className="checkout-layout">
        <div>
          <h2>Delivery address</h2>
          {addressesQuery.isLoading && (
            <LoadingState className="loading-state--inline" label="Loading addresses…" size="sm" />
          )}
          {addressesQuery.isError && (
            <div className="empty">Saved addresses couldn’t be loaded. You can still enter a new address.</div>
          )}

          <div className="address-picker">
            <label className="radio-row">
              <input
                type="radio"
                name="address-mode"
                checked={mode === "saved"}
                onChange={() => setMode("saved")}
                disabled={addresses.length === 0}
              />
              Use a saved address
            </label>
            {mode === "saved" && (
              <div className="address-list">
                {addresses.map((address) => (
                  <label className="address-card address-card--selectable" key={address.id}>
                    <input
                      type="radio"
                      name="saved-address"
                      checked={effectiveSelectedId === address.id}
                      onChange={() => setSelectedId(address.id)}
                    />
                    <div>
                      <strong>{address.recipientName}</strong>
                      {address.isDefault && <span className="address-default">Default</span>}
                      <div className="address-lines">
                        {address.line1}
                        {address.line2 ? <>, {address.line2}</> : null}
                        <br />
                        {address.city}, {address.state} {address.postalCode}
                      </div>
                    </div>
                  </label>
                ))}
                {addresses.length === 0 && (
                  <div className="empty">
                    No saved addresses.{" "}
                    <button className="linkish" type="button" onClick={() => setMode("new")}>
                      Add one now
                    </button>{" "}
                    or manage them in your{" "}
                    <Link to="/account/addresses">address book</Link>.
                  </div>
                )}
              </div>
            )}

            <label className="radio-row">
              <input
                type="radio"
                name="address-mode"
                checked={mode === "new"}
                onChange={() => setMode("new")}
              />
              Deliver to a new address
            </label>
            {mode === "new" && (
              <div className="auth-form">
                <label>
                  Recipient
                  <input
                    required
                    value={newAddress.recipientName}
                    onChange={(e) => setNewAddress({ ...newAddress, recipientName: e.target.value })}
                  />
                </label>
                <label>
                  Address line 1
                  <input
                    required
                    value={newAddress.line1}
                    onChange={(e) => setNewAddress({ ...newAddress, line1: e.target.value })}
                  />
                </label>
                <label>
                  Address line 2
                  <input
                    value={newAddress.line2 ?? ""}
                    onChange={(e) => setNewAddress({ ...newAddress, line2: e.target.value })}
                  />
                </label>
                <div className="form-row">
                  <label>
                    City
                    <input
                      required
                      value={newAddress.city}
                      onChange={(e) => setNewAddress({ ...newAddress, city: e.target.value })}
                    />
                  </label>
                  <label>
                    State
                    <input
                      required
                      value={newAddress.state}
                      onChange={(e) => setNewAddress({ ...newAddress, state: e.target.value })}
                    />
                  </label>
                </div>
                <div className="form-row">
                  <label>
                    Postal code
                    <input
                      required
                      value={newAddress.postalCode}
                      onChange={(e) => setNewAddress({ ...newAddress, postalCode: e.target.value })}
                    />
                  </label>
                  <label>
                    Country
                    <input
                      required
                      value={newAddress.country ?? "IN"}
                      onChange={(e) => setNewAddress({ ...newAddress, country: e.target.value })}
                    />
                  </label>
                </div>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={Boolean(newAddress.isDefault)}
                    onChange={(e) => setNewAddress({ ...newAddress, isDefault: e.target.checked })}
                  />
                  Save as default
                </label>
              </div>
            )}
          </div>
        </div>

        <aside className="checkout-summary">
          <h2>Order summary</h2>
          <div className="checkout-lines">
            {lines.map((line) => (
              <div className="checkout-line" key={line.sku}>
                <span>
                  {line.name} × {line.quantity}
                </span>
                <span>{formatMoney(line.unitPrice * line.quantity)}</span>
              </div>
            ))}
          </div>
          <div className="cart-summary__total">
            <span>Total (INR)</span>
            <strong>{formatMoney(subtotal)}</strong>
          </div>
          <p className="checkout-hint">
            Payment is authorized at checkout and captured after inventory is reserved.
          </p>
          <div className="checkout-actions">
            <Link className="btn btn-ghost" to="/cart">
              Back to cart
            </Link>
            <button
              className="btn btn-primary"
              disabled={placeOrder.isPending || !canPlace}
              onClick={() => placeOrder.mutate()}
            >
              {placeOrder.isPending ? <Spinner size="sm" label="Paying" /> : null}
              {placeOrder.isPending ? "Paying…" : "Pay & place order"}
            </button>
          </div>
          {disabledReason ? <p className="checkout-hint">{disabledReason}</p> : null}
        </aside>
      </div>
    </div>
  );
}
