import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  DEMO_SCENARIOS,
  buildDemoOrder,
  createOrder,
  randomIdempotencyKey,
  type CreateOrderInput,
  type CreateOrderLineInput,
  type DemoScenarioId,
} from "../api/orders";
import { setPaymentChaos, setShippingChaos } from "../api/chaos";
import { listInventory } from "../api/inventory";
import { formatMoney } from "../lib/format";
import type { StockItem } from "../api/types";

interface CustomLineDraft {
  key: string;
  sku: string;
  quantity: string;
  unitPrice: string;
}

function newLineDraft(stock?: StockItem | null): CustomLineDraft {
  return {
    key: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    sku: stock?.sku ?? "",
    quantity: "1",
    unitPrice: stock ? Number(stock.unitPrice).toFixed(2) : "0.00",
  };
}

export function DemoFlowsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [paymentChaos, setPaymentChaosRate] = useState(0);
  const [shippingChaos, setShippingChaosRate] = useState(0);
  const [chaosMessage, setChaosMessage] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const [customerId, setCustomerId] = useState("cust-admin");
  const [currency, setCurrency] = useState("INR");
  const [lines, setLines] = useState<CustomLineDraft[]>([newLineDraft()]);

  const inventoryQuery = useQuery({
    queryKey: ["inventory"],
    queryFn: listInventory,
  });
  const stockItems = inventoryQuery.data ?? [];
  const stockBySku = useMemo(() => {
    const map = new Map<string, StockItem>();
    for (const item of stockItems) {
      map.set(item.sku, item);
    }
    return map;
  }, [stockItems]);

  const createScenarioMutation = useMutation({
    mutationFn: (scenarioId: DemoScenarioId) =>
      createOrder(buildDemoOrder(scenarioId), randomIdempotencyKey()),
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      navigate(`/orders/${order.id}`);
    },
  });

  const createCustomMutation = useMutation({
    mutationFn: (input: CreateOrderInput) => createOrder(input, randomIdempotencyKey()),
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      navigate(`/orders/${order.id}`);
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const paymentChaosMutation = useMutation({
    mutationFn: (rate: number) => setPaymentChaos(rate),
    onSuccess: (state) => {
      setPaymentChaosRate(state.failureRate);
      setChaosMessage(`Payment chaos set to ${(state.failureRate * 100).toFixed(0)}%`);
    },
  });

  const shippingChaosMutation = useMutation({
    mutationFn: (rate: number) => setShippingChaos(rate),
    onSuccess: (state) => {
      setShippingChaosRate(state.failureRate);
      setChaosMessage(`Shipping chaos set to ${(state.failureRate * 100).toFixed(0)}%`);
    },
  });

  const busy =
    createScenarioMutation.isPending ||
    createCustomMutation.isPending ||
    paymentChaosMutation.isPending ||
    shippingChaosMutation.isPending;

  const customTotal = lines.reduce((sum, line) => {
    const qty = Number(line.quantity);
    const price = Number(line.unitPrice);
    if (Number.isNaN(qty) || Number.isNaN(price)) return sum;
    return sum + qty * price;
  }, 0);

  function updateLine(key: string, patch: Partial<CustomLineDraft>) {
    setLines((prev) => prev.map((line) => (line.key === key ? { ...line, ...patch } : line)));
  }

  function onSkuChange(key: string, sku: string) {
    const stock = stockBySku.get(sku);
    updateLine(key, {
      sku,
      unitPrice: stock ? Number(stock.unitPrice).toFixed(2) : "0.00",
    });
  }

  function submitCustomOrder() {
    setFormError(null);
    if (!customerId.trim()) {
      setFormError("Customer id is required");
      return;
    }
    if (!currency.trim()) {
      setFormError("Currency is required");
      return;
    }
    if (lines.length === 0) {
      setFormError("Add at least one line item");
      return;
    }

    const parsedLines: CreateOrderLineInput[] = [];
    for (const line of lines) {
      if (!line.sku) {
        setFormError("Each line needs a SKU");
        return;
      }
      const quantity = Number(line.quantity);
      const unitPrice = Number(line.unitPrice);
      if (!Number.isInteger(quantity) || quantity < 1) {
        setFormError(`Quantity for ${line.sku} must be a positive integer`);
        return;
      }
      if (Number.isNaN(unitPrice) || unitPrice < 0) {
        setFormError(`Unit price for ${line.sku} must be a non-negative number`);
        return;
      }
      parsedLines.push({ sku: line.sku, quantity, unitPrice });
    }

    createCustomMutation.mutate({
      customerId: customerId.trim(),
      currency: currency.trim().toUpperCase(),
      lines: parsedLines,
    });
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Demo flows</h1>
          <div className="page-header__subtitle">
            Run preset saga scenarios or place a custom order
          </div>
        </div>
        <div className="page-actions">
          <button className="btn" onClick={() => navigate("/")}>
            View orders
          </button>
        </div>
      </div>

      <div className="panel">
        <h2>Preset scenarios</h2>
        <p className="panel-lead">
          Each run creates an order and opens its detail page so you can watch the saga live.
        </p>
        <div className="scenario-grid">
          {DEMO_SCENARIOS.map((scenario) => (
            <div className="scenario-card" key={scenario.id}>
              <div className="scenario-card__body">
                <div className="scenario-card__title">{scenario.title}</div>
                <div className="scenario-card__desc">{scenario.description}</div>
              </div>
              <button
                className={`btn btn-sm${scenario.id === "happy" ? " btn-primary" : ""}`}
                disabled={busy}
                onClick={() => createScenarioMutation.mutate(scenario.id)}
              >
                {createScenarioMutation.isPending && createScenarioMutation.variables === scenario.id
                  ? "Running…"
                  : "Run"}
              </button>
            </div>
          ))}
        </div>

        <div className="chaos-row">
          <div className="chaos-control">
            <span className="chaos-control__label">
              Payment chaos <span className="mono">{(paymentChaos * 100).toFixed(0)}%</span>
            </span>
            <div className="chaos-control__actions">
              <button className="btn btn-sm" disabled={busy} onClick={() => paymentChaosMutation.mutate(0)}>
                Off
              </button>
              <button className="btn btn-sm" disabled={busy} onClick={() => paymentChaosMutation.mutate(0.5)}>
                50%
              </button>
              <button className="btn btn-sm" disabled={busy} onClick={() => paymentChaosMutation.mutate(1)}>
                100%
              </button>
            </div>
          </div>
          <div className="chaos-control">
            <span className="chaos-control__label">
              Shipping chaos <span className="mono">{(shippingChaos * 100).toFixed(0)}%</span>
            </span>
            <div className="chaos-control__actions">
              <button className="btn btn-sm" disabled={busy} onClick={() => shippingChaosMutation.mutate(0)}>
                Off
              </button>
              <button className="btn btn-sm" disabled={busy} onClick={() => shippingChaosMutation.mutate(0.5)}>
                50%
              </button>
              <button className="btn btn-sm" disabled={busy} onClick={() => shippingChaosMutation.mutate(1)}>
                100%
              </button>
            </div>
          </div>
        </div>
        {chaosMessage && <div className="chaos-note">{chaosMessage}</div>}
      </div>

      <div className="panel">
        <h2>Custom order</h2>
        <p className="panel-lead">
          Build an order with your own customer, currency, and line items. Tips: amounts ending in{" "}
          <span className="mono">.99</span> trigger payment failure; customer ids starting with{" "}
          <span className="mono">NOSHIP-</span> trigger shipping failure.
        </p>

        {inventoryQuery.isError && (
          <div className="error-banner">
            Failed to load inventory: {(inventoryQuery.error as Error).message}
          </div>
        )}

        <div className="form-row" style={{ marginBottom: 16 }}>
          <label className="form-field">
            <span>Customer id</span>
            <input
              className="input input-wide"
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
              placeholder="cust-admin or NOSHIP-demo"
            />
          </label>
          <label className="form-field">
            <span>Currency</span>
            <input
              className="input"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              placeholder="INR"
            />
          </label>
        </div>

        <div className="custom-lines">
          {lines.map((line, index) => {
            const stock = stockBySku.get(line.sku);
            return (
              <div className="custom-line" key={line.key}>
                <label className="form-field">
                  <span>SKU{index === 0 ? "" : ` ${index + 1}`}</span>
                  <select
                    className="input input-wide"
                    value={line.sku}
                    onChange={(e) => onSkuChange(line.key, e.target.value)}
                  >
                    <option value="">Select SKU…</option>
                    {stockItems.map((item) => (
                      <option key={item.sku} value={item.sku}>
                        {item.sku} — {item.name} (avail {item.availableQty})
                      </option>
                    ))}
                  </select>
                </label>
                <label className="form-field">
                  <span>Qty</span>
                  <input
                    className="input"
                    type="number"
                    min={1}
                    step={1}
                    value={line.quantity}
                    onChange={(e) => updateLine(line.key, { quantity: e.target.value })}
                  />
                </label>
                <label className="form-field">
                  <span>Unit price</span>
                  <input
                    className="input"
                    type="number"
                    min={0}
                    step="0.01"
                    value={line.unitPrice}
                    onChange={(e) => updateLine(line.key, { unitPrice: e.target.value })}
                  />
                </label>
                <div className="custom-line__meta">
                  {stock && (
                    <span className="mono">
                      catalog {formatMoney(Number(stock.unitPrice))}
                    </span>
                  )}
                  <button
                    className="btn btn-sm btn-danger"
                    disabled={busy || lines.length === 1}
                    onClick={() => setLines((prev) => prev.filter((l) => l.key !== line.key))}
                  >
                    Remove
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        <div className="form-row" style={{ marginTop: 14, justifyContent: "space-between" }}>
          <button
            className="btn btn-sm"
            disabled={busy || stockItems.length === 0}
            onClick={() => setLines((prev) => [...prev, newLineDraft(stockItems[0])])}
          >
            + Add line
          </button>
          <div className="custom-total">
            Total <span className="mono">{formatMoney(customTotal, currency || "INR")}</span>
          </div>
          <button className="btn btn-primary" disabled={busy} onClick={submitCustomOrder}>
            {createCustomMutation.isPending ? "Creating…" : "Place order"}
          </button>
        </div>
      </div>

      {createScenarioMutation.isError && (
        <div className="error-banner">
          Failed to run scenario: {(createScenarioMutation.error as Error).message}
        </div>
      )}
      {(paymentChaosMutation.isError || shippingChaosMutation.isError) && (
        <div className="error-banner">
          Failed to update chaos:{" "}
          {((paymentChaosMutation.error || shippingChaosMutation.error) as Error).message}
        </div>
      )}
      {formError && <div className="error-banner">{formError}</div>}
    </div>
  );
}
