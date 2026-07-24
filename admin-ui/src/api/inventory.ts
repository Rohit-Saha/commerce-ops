import { api } from "./client";
import type { StockItem } from "./types";

export interface CreateStockItemInput {
  sku: string;
  name: string;
  unitPrice: number;
  availableQty?: number;
}

export interface UpdateStockItemInput {
  name: string;
  unitPrice: number;
}

export function listInventory(): Promise<StockItem[]> {
  return api.get<StockItem[]>("/api/inventory");
}

export function createProduct(input: CreateStockItemInput): Promise<StockItem> {
  return api.post<StockItem>("/api/inventory", input);
}

export function updateProduct(sku: string, input: UpdateStockItemInput): Promise<StockItem> {
  return api.put<StockItem>(`/api/inventory/${encodeURIComponent(sku)}`, input);
}

export function deleteProduct(sku: string): Promise<void> {
  return api.delete<void>(`/api/inventory/${encodeURIComponent(sku)}`);
}

export function restock(sku: string, qty: number): Promise<StockItem> {
  return api.post<StockItem>(`/api/inventory/${encodeURIComponent(sku)}/restock?qty=${qty}`);
}
