import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listCatalog } from "../api/catalog";
import {
  cartSubtotal,
  removeFromCart,
  setCartQuantity,
  syncCartAvailability,
} from "../lib/cart";
import { formatMoney } from "../lib/format";
import { useCart } from "../lib/useCart";
import { useEffect } from "react";
import { CartSkeleton } from "../components/Skeletons";

export function CartPage() {
  const { lines } = useCart();

  const catalogQuery = useQuery({
    queryKey: ["catalog"],
    queryFn: listCatalog,
  });

  useEffect(() => {
    if (!catalogQuery.data) return;
    const availability: Record<
      string,
      {
        availableQty: number;
        unitPrice: number;
        name: string;
        slug?: string;
        primaryImageUrl?: string;
      }
    > = {};
    for (const item of catalogQuery.data) {
      availability[item.sku] = {
        availableQty: item.availableQty,
        unitPrice: Number(item.unitPrice),
        name: item.name,
        slug: item.slug,
        primaryImageUrl: item.primaryImageUrl,
      };
    }
    syncCartAvailability(availability);
  }, [catalogQuery.data]);

  const subtotal = cartSubtotal(lines);

  if (catalogQuery.isLoading && lines.length === 0) {
    return (
      <div className="cart-page">
        <h1>Your cart</h1>
        <CartSkeleton />
      </div>
    );
  }

  if (lines.length === 0) {
    return (
      <div className="cart-page">
        <h1>Your cart</h1>
        <div className="cart-empty">
          <h2>Nothing here yet</h2>
          <p>Browse the catalog and add pieces while they’re in stock.</p>
          <Link className="btn btn-primary" to="/catalog">
            Continue shopping
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="cart-page">
      <h1>Your cart</h1>
      <div className="cart-layout">
        <div className="cart-list">
          {lines.map((line) => {
            const href = line.slug ? `/p/${encodeURIComponent(line.slug)}` : undefined;
            return (
              <div className="cart-row" key={line.sku}>
                {href ? (
                  <Link to={href} className="cart-row__thumb">
                    {line.primaryImageUrl ? (
                      <img src={line.primaryImageUrl} alt="" />
                    ) : (
                      <div className="product__swatch" data-sku={line.sku} />
                    )}
                  </Link>
                ) : (
                  <div className="cart-row__thumb">
                    {line.primaryImageUrl ? (
                      <img src={line.primaryImageUrl} alt="" />
                    ) : (
                      <div className="product__swatch" data-sku={line.sku} />
                    )}
                  </div>
                )}
                <div>
                  <div className="cart-row__name">
                    {href ? <Link to={href}>{line.name}</Link> : line.name}
                  </div>
                  <div className="cart-row__sku">{line.sku}</div>
                  <div className="cart-row__price">{formatMoney(line.unitPrice)} each</div>
                </div>
                <div className="cart-row__actions">
                  <div className="qty-stepper" aria-label={`Quantity for ${line.name}`}>
                    <button
                      type="button"
                      disabled={line.quantity <= 1}
                      onClick={() => setCartQuantity(line.sku, line.quantity - 1)}
                    >
                      −
                    </button>
                    <span>{line.quantity}</span>
                    <button
                      type="button"
                      disabled={line.quantity >= line.availableQty}
                      onClick={() => setCartQuantity(line.sku, line.quantity + 1)}
                    >
                      +
                    </button>
                  </div>
                  <button className="btn btn-ghost" onClick={() => removeFromCart(line.sku)}>
                    Remove
                  </button>
                </div>
                <div className="cart-row__total">{formatMoney(line.unitPrice * line.quantity)}</div>
              </div>
            );
          })}
        </div>
        <aside className="cart-summary">
          <div className="cart-summary__total">
            <span>Subtotal</span>
            <strong>{formatMoney(subtotal)}</strong>
          </div>
          <Link className="btn btn-primary" to="/checkout">
            Checkout
          </Link>
          <Link className="btn btn-ghost" to="/catalog">
            Continue shopping
          </Link>
        </aside>
      </div>
    </div>
  );
}
