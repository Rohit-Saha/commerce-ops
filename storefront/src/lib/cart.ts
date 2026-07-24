export interface CartLine {
  sku: string;
  name: string;
  unitPrice: number;
  quantity: number;
  availableQty: number;
  slug?: string;
  primaryImageUrl?: string;
}

const CART_KEY = "northline.cart";
const EMPTY_CART: CartLine[] = [];

type CartListener = () => void;

const listeners = new Set<CartListener>();

/** Cached snapshot for useSyncExternalStore — must be referentially stable when unchanged. */
let snapshot: CartLine[] = loadFromStorage();

function loadFromStorage(): CartLine[] {
  try {
    const raw = localStorage.getItem(CART_KEY);
    if (!raw) return EMPTY_CART;
    const parsed = JSON.parse(raw) as CartLine[];
    return Array.isArray(parsed) && parsed.length > 0 ? parsed : EMPTY_CART;
  } catch {
    return EMPTY_CART;
  }
}

function sameCart(a: CartLine[], b: CartLine[]): boolean {
  if (a === b) return true;
  if (a.length !== b.length) return false;
  return a.every((line, i) => {
    const other = b[i];
    return (
      line.sku === other.sku &&
      line.name === other.name &&
      line.unitPrice === other.unitPrice &&
      line.quantity === other.quantity &&
      line.availableQty === other.availableQty &&
      line.slug === other.slug &&
      line.primaryImageUrl === other.primaryImageUrl
    );
  });
}

function writeCart(lines: CartLine[]): CartLine[] {
  const next = lines.length === 0 ? EMPTY_CART : lines;
  if (sameCart(snapshot, next)) {
    return snapshot;
  }
  snapshot = next;
  if (next === EMPTY_CART) {
    localStorage.removeItem(CART_KEY);
  } else {
    localStorage.setItem(CART_KEY, JSON.stringify(next));
  }
  listeners.forEach((listener) => listener());
  return snapshot;
}

export function subscribeCart(listener: CartListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getCart(): CartLine[] {
  return snapshot;
}

export function cartItemCount(lines: CartLine[] = snapshot): number {
  return lines.reduce((sum, line) => sum + line.quantity, 0);
}

export function cartSubtotal(lines: CartLine[] = snapshot): number {
  return lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0);
}

export function addToCart(item: Omit<CartLine, "quantity">, quantity = 1): CartLine[] {
  const lines = snapshot;
  const existing = lines.find((line) => line.sku === item.sku);
  const nextQty = Math.min(item.availableQty, (existing?.quantity ?? 0) + quantity);
  if (nextQty <= 0) {
    return lines;
  }
  let next: CartLine[];
  if (existing) {
    next = lines.map((line) =>
      line.sku === item.sku ? { ...line, ...item, quantity: nextQty } : line,
    );
  } else {
    next = [...lines, { ...item, quantity: nextQty }];
  }
  return writeCart(next);
}

export function setCartQuantity(sku: string, quantity: number): CartLine[] {
  const lines = snapshot;
  const next =
    quantity <= 0
      ? lines.filter((line) => line.sku !== sku)
      : lines.map((line) => {
          if (line.sku !== sku) return line;
          return { ...line, quantity: Math.min(line.availableQty, quantity) };
        });
  return writeCart(next);
}

export function removeFromCart(sku: string): CartLine[] {
  return writeCart(snapshot.filter((line) => line.sku !== sku));
}

export function clearCart(): void {
  writeCart(EMPTY_CART);
}

export function syncCartAvailability(
  availability: Record<
    string,
    {
      availableQty: number;
      unitPrice: number;
      name: string;
      slug?: string;
      primaryImageUrl?: string;
    }
  >,
): CartLine[] {
  const next = snapshot
    .map((line): CartLine | null => {
      const stock = availability[line.sku];
      if (!stock) {
        return null;
      }
      return {
        ...line,
        name: stock.name,
        unitPrice: stock.unitPrice,
        availableQty: stock.availableQty,
        slug: stock.slug ?? line.slug,
        primaryImageUrl: stock.primaryImageUrl ?? line.primaryImageUrl,
        quantity: Math.min(line.quantity, Math.max(0, stock.availableQty)),
      };
    })
    .filter((line): line is CartLine => line !== null && line.quantity > 0);
  return writeCart(next);
}
