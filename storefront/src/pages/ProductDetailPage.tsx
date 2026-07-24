import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { useState } from "react";
import { getCatalogBySlug, searchCatalog } from "../api/catalog";
import { addToCart } from "../lib/cart";
import { formatMoney } from "../lib/format";
import { useNotify, useNotifyQueryError } from "../lib/notify";
import { useToast } from "../lib/toast";
import { PdpSkeleton } from "../components/Skeletons";
import { Spinner } from "../components/Spinner";

export function ProductDetailPage() {
  const { slug = "" } = useParams();
  const { pushToast } = useToast();
  const { notifyError } = useNotify();
  const [qty, setQty] = useState(1);
  const [activeImage, setActiveImage] = useState(0);
  const [justAdded, setJustAdded] = useState(false);

  const productQuery = useQuery({
    queryKey: ["catalog-slug", slug],
    queryFn: () => getCatalogBySlug(slug),
    enabled: Boolean(slug),
  });
  useNotifyQueryError(productQuery.isError, productQuery.error, "catalog");

  const item = productQuery.data;
  const relatedQuery = useQuery({
    queryKey: ["related", item?.categorySlug, item?.sku],
    queryFn: () => searchCatalog({ category: item!.categorySlug, size: 8 }),
    enabled: Boolean(item?.categorySlug),
  });

  const addMutation = useMutation({
    mutationFn: async () => {
      if (!item) return;
      addToCart(
        {
          sku: item.sku,
          name: item.name,
          unitPrice: Number(item.unitPrice),
          availableQty: item.availableQty,
          slug: item.slug,
          primaryImageUrl: item.primaryImageUrl,
        },
        qty,
      );
    },
    onSuccess: () => {
      setJustAdded(true);
      pushToast(`Added ${qty} × ${item?.name ?? "item"} to cart`);
    },
    onError: (err: Error) => notifyError(err, "cart"),
  });

  if (productQuery.isLoading) {
    return (
      <div className="pdp">
        <PdpSkeleton />
      </div>
    );
  }

  if (productQuery.isError || !item) {
    return (
      <div className="empty">
        <p>Product not found.</p>
        <Link to="/catalog">Back to catalog</Link>
      </div>
    );
  }

  const images = item.imageUrls?.length
    ? item.imageUrls
    : item.primaryImageUrl
      ? [item.primaryImageUrl]
      : [];
  const maxQty = Math.max(1, item.availableQty);
  const related = (relatedQuery.data?.items ?? [])
    .filter((p) => p.sku !== item.sku)
    .slice(0, 4);

  return (
    <div className="pdp">
      <Link className="pdp__back" to="/catalog">
        ← Catalog
      </Link>
      <div className="pdp__grid">
        <div className="pdp__gallery">
          {images.length > 0 ? (
            <>
              <img
                className="pdp__main-image"
                key={images[Math.min(activeImage, images.length - 1)]}
                src={images[Math.min(activeImage, images.length - 1)]}
                alt=""
              />
              {images.length > 1 ? (
                <div className="pdp__thumbs">
                  {images.map((url, index) => (
                    <button
                      key={url}
                      type="button"
                      className={`pdp__thumb${activeImage === index ? " active" : ""}`}
                      onClick={() => setActiveImage(index)}
                      aria-label={`Image ${index + 1}`}
                    >
                      <img src={url} alt="" />
                    </button>
                  ))}
                </div>
              ) : null}
            </>
          ) : (
            <div className="product__swatch pdp__swatch" data-sku={item.sku} />
          )}
        </div>
        <div className="pdp__copy">
          {item.categoryName ? <p className="pdp__category">{item.categoryName}</p> : null}
          <h1>{item.name}</h1>
          <p className="pdp__sku">{item.sku}</p>
          <p className="pdp__price">{formatMoney(Number(item.unitPrice))}</p>
          <p className={`stock ${item.inStock ? "in" : "out"}`}>
            {item.inStock ? `${item.availableQty} in stock` : "Sold out"}
          </p>
          {item.shortDescription ? <p className="pdp__lead">{item.shortDescription}</p> : null}
          {item.bodyText ? <div className="pdp__body">{item.bodyText}</div> : null}
          {item.tags && item.tags.length > 0 ? (
            <ul className="pdp__tags">
              {item.tags.map((t) => (
                <li key={t}>{t}</li>
              ))}
            </ul>
          ) : null}

          {item.inStock ? (
            <div className="pdp__actions">
              <div className="qty-stepper" aria-label="Quantity">
                <button
                  type="button"
                  disabled={qty <= 1}
                  onClick={() => setQty((q) => Math.max(1, q - 1))}
                >
                  −
                </button>
                <span>{qty}</span>
                <button
                  type="button"
                  disabled={qty >= maxQty}
                  onClick={() => setQty((q) => Math.min(maxQty, q + 1))}
                >
                  +
                </button>
              </div>
              <button
                className="btn btn-primary"
                disabled={addMutation.isPending}
                onClick={() => addMutation.mutate()}
              >
                {addMutation.isPending ? <Spinner size="sm" label="Adding" /> : null}
                {addMutation.isPending ? "Adding…" : "Add to cart"}
              </button>
            </div>
          ) : (
            <button className="btn btn-primary" disabled>
              Unavailable
            </button>
          )}

          {justAdded ? (
            <div className="pdp__post-add">
              <Link className="btn btn-primary" to="/cart">
                View cart
              </Link>
              <Link className="btn btn-ghost" to="/catalog">
                Continue shopping
              </Link>
            </div>
          ) : null}
        </div>
      </div>

      {related.length > 0 ? (
        <section className="related">
          <h2>More in {item.categoryName ?? "this category"}</h2>
          <div className="product-grid">
            {related.map((rel) => {
              const href = rel.slug ? `/p/${encodeURIComponent(rel.slug)}` : undefined;
              return (
                <article className="product" key={rel.sku}>
                  {href ? (
                    <Link to={href} className="product__media">
                      {rel.primaryImageUrl ? (
                        <img src={rel.primaryImageUrl} alt="" loading="lazy" />
                      ) : (
                        <div className="product__swatch" data-sku={rel.sku} />
                      )}
                    </Link>
                  ) : (
                    <div className="product__swatch" data-sku={rel.sku} />
                  )}
                  <div className="product__body">
                    <h3>{href ? <Link to={href}>{rel.name}</Link> : rel.name}</h3>
                    <div className="product__meta">
                      <span className="product__price">{formatMoney(Number(rel.unitPrice))}</span>
                      <span className={`stock ${rel.inStock ? "in" : "out"}`}>
                        {rel.inStock ? "In stock" : "Sold out"}
                      </span>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </section>
      ) : null}
    </div>
  );
}
