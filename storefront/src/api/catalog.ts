import { api } from "./client";
import type { CatalogItem, CatalogSearchResult } from "./types";

/** API returns displayTitle / inventoryName; storefront code expects a stable `name`. */
function normalizeItem(raw: CatalogItem): CatalogItem {
  const name =
    raw.displayTitle?.trim() ||
    raw.name?.trim() ||
    raw.inventoryName?.trim() ||
    raw.sku;
  return { ...raw, name };
}

function normalizeSearch(result: CatalogSearchResult): CatalogSearchResult {
  return {
    ...result,
    items: (result.items ?? []).map(normalizeItem),
  };
}

export function listCatalog(): Promise<CatalogItem[]> {
  return api.get<CatalogItem[]>("/api/store/catalog").then((items) => items.map(normalizeItem));
}

export function getCatalogItem(sku: string): Promise<CatalogItem> {
  return api
    .get<CatalogItem>(`/api/store/catalog/${encodeURIComponent(sku)}`)
    .then(normalizeItem);
}

export function getCatalogBySlug(slug: string): Promise<CatalogItem> {
  return api
    .get<CatalogItem>(`/api/store/catalog/by-slug/${encodeURIComponent(slug)}`)
    .then(normalizeItem);
}

export function searchCatalog(params: {
  q?: string;
  category?: string;
  tag?: string;
  inStock?: boolean;
  page?: number;
  size?: number;
}): Promise<CatalogSearchResult> {
  const query = new URLSearchParams();
  if (params.q) query.set("q", params.q);
  if (params.category) query.set("category", params.category);
  if (params.tag) query.set("tag", params.tag);
  if (params.inStock != null) query.set("inStock", String(params.inStock));
  if (params.page != null) query.set("page", String(params.page));
  if (params.size != null) query.set("size", String(params.size));
  const qs = query.toString();
  return api
    .get<CatalogSearchResult>(`/api/store/catalog/search${qs ? `?${qs}` : ""}`)
    .then(normalizeSearch);
}

export function listCategories(): Promise<{ slug: string; name: string; count: number }[]> {
  return api.get("/api/store/catalog/categories");
}
