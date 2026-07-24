import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listCategories, searchCatalog } from "../api/catalog";
import { formatMoney } from "../lib/format";
import { ProductGridSkeleton } from "../components/Skeletons";

export function HomePage() {
  const featuredQuery = useQuery({
    queryKey: ["home-featured"],
    queryFn: () => searchCatalog({ size: 4, inStock: true }),
  });

  const categoriesQuery = useQuery({
    queryKey: ["home-categories"],
    queryFn: listCategories,
  });

  const featured = featuredQuery.data?.items ?? [];
  const categories = categoriesQuery.data ?? [];

  return (
    <div className="home">
      <section className="hero">
        <div className="hero__media" aria-hidden="true">
          <img src="/northline-hero-editorial.jpg" alt="" />
          <div className="hero__veil" />
        </div>
        <div className="hero__copy">
          <p className="hero__brand">Northline</p>
          <h1>Everyday goods, made to last.</h1>
          <p className="hero__lead">
            Quiet essentials with live stock. Sign in when you’re ready to check out and track your
            order.
          </p>
          <Link className="btn btn-primary" to="/catalog">
            Shop the catalog
          </Link>
        </div>
      </section>

      <div className="page-inner home-sections">
        <section className="home-section">
          <div className="section-heading">
            <h2>In the collection</h2>
            <Link className="section-link" to="/catalog">
              View all
            </Link>
          </div>
          {featuredQuery.isLoading ? (
            <ProductGridSkeleton count={4} />
          ) : featured.length === 0 ? (
            <div className="empty">No published products yet. Check back after Strapi publish.</div>
          ) : (
            <div className="product-grid home-featured">
              {featured.map((item) => {
                const href = item.slug ? `/p/${encodeURIComponent(item.slug)}` : "/catalog";
                return (
                  <article className="product" key={item.sku}>
                    <Link to={href} className="product__media">
                      {item.primaryImageUrl ? (
                        <img src={item.primaryImageUrl} alt="" loading="lazy" />
                      ) : (
                        <div className="product__swatch" data-sku={item.sku} />
                      )}
                    </Link>
                    <div className="product__body">
                      <h3>
                        <Link to={href}>{item.name}</Link>
                      </h3>
                      <div className="product__meta">
                        <span className="product__price">{formatMoney(Number(item.unitPrice))}</span>
                        <span className={`stock ${item.inStock ? "in" : "out"}`}>
                          {item.inStock ? "In stock" : "Sold out"}
                        </span>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>

        <section className="home-section">
          <div className="section-heading">
            <h2>Shop by category</h2>
            <p>Jump straight into a collection</p>
          </div>
          {categoriesQuery.isLoading ? (
            <div className="category-row" aria-hidden="true">
              {[0, 1, 2].map((i) => (
                <div className="skeleton category-tile skeleton-media" key={i} />
              ))}
            </div>
          ) : categories.length === 0 ? (
            <div className="empty">Categories appear once products are published with a category.</div>
          ) : (
            <div className="category-row">
              {categories.map((cat) => (
                <Link
                  key={cat.slug}
                  className="category-tile"
                  to={`/catalog?category=${encodeURIComponent(cat.slug)}`}
                >
                  <span className="category-tile__name">{cat.name}</span>
                  <span className="category-tile__meta">
                    {cat.count} piece{cat.count === 1 ? "" : "s"}
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
