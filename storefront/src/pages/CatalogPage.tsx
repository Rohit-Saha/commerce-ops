import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { useState } from "react";
import { searchCatalog } from "../api/catalog";
import { addToCart } from "../lib/cart";
import { formatMoney } from "../lib/format";
import { useCart } from "../lib/useCart";
import { useNotify, useNotifyQueryError } from "../lib/notify";
import { useToast } from "../lib/toast";
import type { CatalogItem } from "../api/types";
import { ProductGridSkeleton } from "../components/Skeletons";
import { Spinner } from "../components/Spinner";

function FacetPanel({
  category,
  tag,
  facets,
  onCategory,
  onTag,
}: {
  category?: string;
  tag?: string;
  facets?: { categories: { value: string; count: number }[]; tags: { value: string; count: number }[] };
  onCategory: (value?: string) => void;
  onTag: (value?: string) => void;
}) {
  return (
    <>
      <div className="facet-group">
        <h3>Categories</h3>
        <button
          type="button"
          className={`facet-chip${!category ? " active" : ""}`}
          onClick={() => onCategory(undefined)}
        >
          All
        </button>
        {(facets?.categories ?? []).map((bucket) => (
          <button
            key={bucket.value}
            type="button"
            className={`facet-chip${category === bucket.value ? " active" : ""}`}
            onClick={() => onCategory(bucket.value)}
          >
            {bucket.value} <span>{bucket.count}</span>
          </button>
        ))}
      </div>
      <div className="facet-group">
        <h3>Tags</h3>
        <button
          type="button"
          className={`facet-chip${!tag ? " active" : ""}`}
          onClick={() => onTag(undefined)}
        >
          All
        </button>
        {(facets?.tags ?? []).map((bucket) => (
          <button
            key={bucket.value}
            type="button"
            className={`facet-chip${tag === bucket.value ? " active" : ""}`}
            onClick={() => onTag(bucket.value)}
          >
            {bucket.value} <span>{bucket.count}</span>
          </button>
        ))}
      </div>
    </>
  );
}

export function CatalogPage() {
  const { count } = useCart();
  const { pushToast } = useToast();
  const { notifyError } = useNotify();
  const [searchParams, setSearchParams] = useSearchParams();
  const category = searchParams.get("category") || undefined;
  const [q, setQ] = useState("");
  const [draftQ, setDraftQ] = useState("");
  const [tag, setTag] = useState<string | undefined>();
  const [inStockOnly, setInStockOnly] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const catalogQuery = useQuery({
    queryKey: ["catalog-search", q, category, tag, inStockOnly],
    queryFn: () =>
      searchCatalog({
        q: q || undefined,
        category,
        tag,
        inStock: inStockOnly ? true : undefined,
        size: 48,
      }),
    refetchInterval: 20000,
  });
  useNotifyQueryError(catalogQuery.isError, catalogQuery.error, "catalog");

  const addMutation = useMutation({
    mutationFn: async (item: CatalogItem) => {
      addToCart({
        sku: item.sku,
        name: item.name,
        unitPrice: Number(item.unitPrice),
        availableQty: item.availableQty,
        slug: item.slug,
        primaryImageUrl: item.primaryImageUrl,
      });
      return item;
    },
    onSuccess: (item) => {
      pushToast(`Added ${item.name} to cart`);
    },
    onError: (err: Error) => notifyError(err, "cart"),
  });

  const items = catalogQuery.data?.items ?? [];
  const facets = catalogQuery.data?.facets;
  const total = catalogQuery.data?.total ?? 0;

  function closeFilters() {
    setFiltersOpen(false);
  }

  function setCategory(value?: string) {
    const next = new URLSearchParams(searchParams);
    if (value) {
      next.set("category", value);
    } else {
      next.delete("category");
    }
    setSearchParams(next, { replace: true });
  }

  return (
    <div className="page-inner catalog-page">
      <section className="catalog">
        <div className="section-heading">
          <h1 className="catalog-page__title">Catalog</h1>
          <p>
            {catalogQuery.isLoading ? "…" : `${total} product${total === 1 ? "" : "s"}`}
            {count > 0 ? ` · ${count} in cart` : ""}
          </p>
        </div>

        <form
          className="catalog-toolbar"
          onSubmit={(e) => {
            e.preventDefault();
            setQ(draftQ.trim());
          }}
        >
          <input
            className="catalog-search"
            type="search"
            placeholder="Search products…"
            value={draftQ}
            onChange={(e) => setDraftQ(e.target.value)}
            aria-label="Search catalog"
          />
          <button className="btn btn-primary" type="submit">
            Search
          </button>
          <label className="catalog-check">
            <input
              type="checkbox"
              checked={inStockOnly}
              onChange={(e) => setInStockOnly(e.target.checked)}
            />
            In stock only
          </label>
        </form>

        <button
          type="button"
          className="btn btn-ghost facet-sheet-toggle"
          onClick={() => setFiltersOpen(true)}
        >
          Filters
        </button>

        <div
          className={`facet-sheet-backdrop${filtersOpen ? " open" : ""}`}
          onClick={closeFilters}
          aria-hidden={!filtersOpen}
        />

        <div className="catalog-layout">
          <aside className={`catalog-facets${filtersOpen ? " open" : ""}`} aria-label="Filters">
            <FacetPanel
              category={category}
              tag={tag}
              facets={facets}
              onCategory={(value) => {
                setCategory(value);
                closeFilters();
              }}
              onTag={(value) => {
                setTag(value);
                closeFilters();
              }}
            />
          </aside>

          <div className="catalog-results">
            {catalogQuery.isError && (
              <div className="empty">Catalog couldn’t be loaded. Check the banner above and try again.</div>
            )}

            {catalogQuery.isLoading ? (
              <ProductGridSkeleton />
            ) : items.length === 0 ? (
              <div className="empty">No published products match these filters.</div>
            ) : (
              <div className="product-grid">
                {items.map((item) => {
                  const price = Number(item.unitPrice);
                  const pending = addMutation.isPending && addMutation.variables?.sku === item.sku;
                  const href = item.slug ? `/p/${encodeURIComponent(item.slug)}` : undefined;
                  return (
                    <article className="product" key={item.sku}>
                      {href ? (
                        <Link to={href} className="product__media">
                          {item.primaryImageUrl ? (
                            <img src={item.primaryImageUrl} alt="" loading="lazy" />
                          ) : (
                            <div className="product__swatch" data-sku={item.sku} />
                          )}
                        </Link>
                      ) : item.primaryImageUrl ? (
                        <div className="product__media">
                          <img src={item.primaryImageUrl} alt="" loading="lazy" />
                        </div>
                      ) : (
                        <div className="product__swatch" data-sku={item.sku} />
                      )}
                      <div className="product__body">
                        <h3>{href ? <Link to={href}>{item.name}</Link> : item.name}</h3>
                        {item.shortDescription ? (
                          <p className="product__blurb">{item.shortDescription}</p>
                        ) : (
                          <p className="product__sku">{item.sku}</p>
                        )}
                        <div className="product__meta">
                          <span className="product__price">{formatMoney(price)}</span>
                          <span className={`stock ${item.inStock ? "in" : "out"}`}>
                            {item.inStock ? `${item.availableQty} in stock` : "Sold out"}
                          </span>
                        </div>
                        <button
                          className="btn btn-primary"
                          disabled={!item.inStock || pending}
                          onClick={() => addMutation.mutate(item)}
                        >
                          {pending ? <Spinner size="sm" label="Adding" /> : null}
                          {pending ? "Adding…" : item.inStock ? "Add to cart" : "Unavailable"}
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
