export function ProductGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="product-grid" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div className="skeleton-card" key={i}>
          <div className="skeleton skeleton-media" />
          <div className="skeleton skeleton-line" />
          <div className="skeleton skeleton-line short" />
          <div className="skeleton skeleton-btn" />
        </div>
      ))}
    </div>
  );
}

export function PdpSkeleton() {
  return (
    <div className="pdp-skeleton" aria-hidden="true">
      <div className="skeleton skeleton-media tall" />
      <div className="pdp-skeleton__copy">
        <div className="skeleton skeleton-line short" />
        <div className="skeleton skeleton-line title" />
        <div className="skeleton skeleton-line" />
        <div className="skeleton skeleton-line" />
        <div className="skeleton skeleton-btn" />
      </div>
    </div>
  );
}

export function CartSkeleton() {
  return (
    <div className="cart-skeleton" aria-hidden="true">
      {Array.from({ length: 3 }, (_, i) => (
        <div className="skeleton-cart-row" key={i}>
          <div className="skeleton skeleton-thumb" />
          <div className="skeleton-cart-copy">
            <div className="skeleton skeleton-line" />
            <div className="skeleton skeleton-line short" />
          </div>
        </div>
      ))}
    </div>
  );
}
