import { useSyncExternalStore } from "react";
import { cartItemCount, getCart, subscribeCart, type CartLine } from "./cart";

export function useCart(): { lines: CartLine[]; count: number } {
  const lines = useSyncExternalStore(subscribeCart, getCart, getCart);
  return { lines, count: cartItemCount(lines) };
}
