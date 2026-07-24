package com.commerceops.catalog.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.commerceops.catalog.domain.CatalogProduct;
import com.commerceops.catalog.repository.CatalogProductRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CatalogSearchIndexService {

    public static final String INDEX = "catalog_products";
    private static final Logger log = LoggerFactory.getLogger(CatalogSearchIndexService.class);

    private final ElasticsearchClient es;
    private final CatalogProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public CatalogSearchIndexService(
            ElasticsearchClient es,
            CatalogProductRepository productRepository,
            ObjectMapper objectMapper) {
        this.es = es;
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
    }

    public void ensureIndex() {
        try {
            boolean exists = es.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
            if (exists) {
                return;
            }
            es.indices().create(c -> c
                    .index(INDEX)
                    .mappings(m -> m
                            .properties("sku", p -> p.keyword(k -> k))
                            .properties("displayTitle", p -> p.text(t -> t))
                            .properties("inventoryName", p -> p.text(t -> t))
                            .properties("slug", p -> p.keyword(k -> k))
                            .properties("shortDescription", p -> p.text(t -> t))
                            .properties("bodyText", p -> p.text(t -> t))
                            .properties("categorySlug", p -> p.keyword(k -> k))
                            .properties("categoryName", p -> p.keyword(k -> k))
                            .properties("tags", p -> p.keyword(k -> k))
                            .properties("imageUrls", p -> p.keyword(k -> k))
                            .properties("primaryImageUrl", p -> p.keyword(k -> k))
                            .properties("unitPrice", p -> p.double_(d -> d))
                            .properties("availableQty", p -> p.integer(i -> i))
                            .properties("inStock", p -> p.boolean_(b -> b))
                            .properties("publishedAt", p -> p.date(d -> d))
                            .properties("updatedAt", p -> p.date(d -> d))
                    ));
            log.info("Created Elasticsearch index {}", INDEX);
        } catch (Exception ex) {
            log.warn("Failed to ensure ES index: {}", ex.getMessage());
        }
    }

    public void indexProduct(CatalogProduct product) {
        if (product.isDeleted() || !product.isCmsPublished() || product.getSlug() == null || product.getSlug().isBlank()) {
            deleteProduct(product.getSku());
            return;
        }
        try {
            CatalogDocument doc = toDocument(product);
            es.index(i -> i.index(INDEX).id(product.getSku()).document(doc));
        } catch (Exception ex) {
            log.warn("ES index failed for sku={}: {}", product.getSku(), ex.getMessage());
        }
    }

    public void deleteProduct(String sku) {
        try {
            es.delete(d -> d.index(INDEX).id(sku));
        } catch (Exception ex) {
            log.debug("ES delete sku={}: {}", sku, ex.getMessage());
        }
    }

    public void reindexAllPublished() {
        ensureIndex();
        List<CatalogProduct> products = productRepository.findByDeletedFalseAndCmsPublishedTrue();
        for (CatalogProduct product : products) {
            indexProduct(product);
        }
        log.info("Reindexed {} published catalog products into ES", products.size());
    }

    public void reindexIfEmpty() {
        ensureIndex();
        try {
            long count = es.count(c -> c.index(INDEX)).count();
            if (count == 0) {
                reindexAllPublished();
            }
        } catch (Exception ex) {
            log.warn("ES reindex-if-empty failed: {}", ex.getMessage());
        }
    }

    public SearchResult search(String q, String category, String tag, Boolean inStock, int page, int size) {
        ensureIndex();
        int from = Math.max(0, page) * Math.max(1, size);
        int pageSize = Math.min(50, Math.max(1, size));
        try {
            BoolQuery.Builder bool = new BoolQuery.Builder();
            if (q != null && !q.isBlank()) {
                bool.must(Query.of(qq -> qq.multiMatch(mm -> mm
                        .query(q)
                        .fields("displayTitle^3", "inventoryName^2", "shortDescription", "bodyText", "tags"))));
            } else {
                bool.must(Query.of(qq -> qq.matchAll(m -> m)));
            }
            if (category != null && !category.isBlank()) {
                bool.filter(Query.of(qq -> qq.term(t -> t.field("categorySlug").value(category))));
            }
            if (tag != null && !tag.isBlank()) {
                bool.filter(Query.of(qq -> qq.term(t -> t.field("tags").value(tag))));
            }
            if (inStock != null) {
                bool.filter(Query.of(qq -> qq.term(t -> t.field("inStock").value(inStock))));
            }

            boolean hasQuery = q != null && !q.isBlank();
            SearchResponse<CatalogDocument> response = es.search(s -> {
                var search = s
                        .index(INDEX)
                        .from(from)
                        .size(pageSize)
                        .query(Query.of(qq -> qq.bool(bool.build())))
                        .aggregations("categories", Aggregation.of(a -> a.terms(t -> t.field("categorySlug").size(50))))
                        .aggregations("tags", Aggregation.of(a -> a.terms(t -> t.field("tags").size(50))));
                // Stable ordering: relevance then SKU when searching; SKU alone when browsing.
                if (hasQuery) {
                    search = search
                            .sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
                            .sort(so -> so.field(f -> f.field("sku").order(SortOrder.Asc)));
                } else {
                    search = search.sort(so -> so.field(f -> f.field("sku").order(SortOrder.Asc)));
                }
                return search;
            }, CatalogDocument.class);

            List<CatalogDocument> items = new ArrayList<>();
            for (Hit<CatalogDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    items.add(hit.source());
                }
            }
            List<FacetBucket> categories = facetBuckets(response, "categories");
            List<FacetBucket> tags = facetBuckets(response, "tags");
            long total = response.hits().total() != null ? response.hits().total().value() : items.size();
            return new SearchResult(items, new Facets(categories, tags), total);
        } catch (Exception ex) {
            log.warn("ES search failed, falling back to DB: {}", ex.getMessage());
            return fallbackSearch(q, category, tag, inStock, from, pageSize);
        }
    }

    private SearchResult fallbackSearch(String q, String category, String tag, Boolean inStock, int from, int size) {
        List<CatalogProduct> all = productRepository.findByDeletedFalseAndCmsPublishedTrueOrderBySkuAsc();
        List<CatalogDocument> filtered = all.stream()
                .map(this::toDocument)
                .filter(d -> q == null || q.isBlank()
                        || containsIgnoreCase(d.displayTitle(), q)
                        || containsIgnoreCase(d.shortDescription(), q)
                        || containsIgnoreCase(d.bodyText(), q))
                .filter(d -> category == null || category.isBlank() || category.equals(d.categorySlug()))
                .filter(d -> tag == null || tag.isBlank() || (d.tags() != null && d.tags().contains(tag)))
                .filter(d -> inStock == null || d.inStock() == inStock)
                .toList();
        List<CatalogDocument> page = filtered.stream().skip(from).limit(size).toList();
        return new SearchResult(page, new Facets(List.of(), List.of()), filtered.size());
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private List<FacetBucket> facetBuckets(SearchResponse<CatalogDocument> response, String name) {
        if (response.aggregations() == null || !response.aggregations().containsKey(name)) {
            return List.of();
        }
        List<FacetBucket> buckets = new ArrayList<>();
        for (StringTermsBucket bucket : response.aggregations().get(name).sterms().buckets().array()) {
            buckets.add(new FacetBucket(bucket.key().stringValue(), bucket.docCount()));
        }
        return buckets;
    }

    public CatalogDocument toDocument(CatalogProduct product) {
        return new CatalogDocument(
                product.getSku(),
                product.storefrontTitle(),
                product.getName(),
                product.getSlug(),
                product.getShortDescription(),
                product.getBodyText(),
                product.getCategorySlug(),
                product.getCategoryName(),
                readStringList(product.getTagsJson()),
                readStringList(product.getImageUrlsJson()),
                product.getPrimaryImageUrl(),
                product.getUnitPrice(),
                product.getAvailableQty(),
                product.getAvailableQty() > 0,
                format(product.getCmsPublishedAt()),
                format(product.getUpdatedAt()));
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String format(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    public record FacetBucket(String value, long count) {}
    public record Facets(List<FacetBucket> categories, List<FacetBucket> tags) {}
    public record SearchResult(List<CatalogDocument> items, Facets facets, long total) {}
}
